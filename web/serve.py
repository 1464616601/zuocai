#!/usr/bin/env python3
"""菜谱网页版本地服务。

作用：
  1. 提供静态文件（替代 python -m http.server）
  2. 提供 /api/chat 代理，把请求转发给大模型

为什么要代理：智谱的接口只在 POST 响应里给 CORS 头，OPTIONS 预检不给，
浏览器在预检阶段就会拦下，前端直连必然失败。走本地服务端转发可以完全绕开。

API Key 只存在本机 config.local.json，不会进入浏览器，也不会被打包进 HTML。
"""
import base64
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from html import unescape
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(HERE, "config.local.json")
PORT = int(os.environ.get("ZUOCAI_PORT", "8765"))

# 出厂自带的 Key：config.local.json 没填或不存在时兜底，保证开箱即用。
# 用户在 config.local.json 填了自己的 Key 后以用户的为准。
DEFAULT_API_KEY = "0214776f7c53423bb64eb4c20261dc0b.j8uG29SXkMoy0uNf"

BROWSER_UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/130.0 Safari/537.36")

# 付费图库站点：搜出来的图带明显水印，直接剔除
# 付费图库站点：搜出来的图铺满大写水印，基本不可用，直接剔除。
# 只拦这一类；知乎/新浪/百度经验/花瓣等来源的图质量尚可，留给用户自己挑。
WATERMARK_HOSTS = (
    "699pic.com", "588ku.com", "zhitu.com", "tukuppt.com", "veer.com",
    "gettyimages", "shutterstock", "dreamstime", "alamy.com", "123rf.com",
    "photophoto.cn", "tuchong.com", "ivsky.com", "51yuansu.com", "pngtree",
    "lovepik.com", "bigstock", "depositphotos", "istockphoto", "huitu.com",
    "nipic.com", "freep.cn",
)


def http_get(url, referer=None, timeout=20, max_bytes=6 * 1024 * 1024):
    headers = {"User-Agent": BROWSER_UA, "Accept": "image/*,*/*;q=0.8"}
    if referer:
        headers["Referer"] = referer
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read(max_bytes)


def load_config():
    if not os.path.exists(CONFIG_PATH):
        cfg = {}
    else:
        try:
            with open(CONFIG_PATH, encoding="utf-8") as f:
                cfg = json.load(f)
        except Exception as e:
            print("[warn] config.local.json 解析失败：%s" % e)
            cfg = {}
    if not (cfg.get("api_key") or "").strip():
        cfg["api_key"] = DEFAULT_API_KEY
    return cfg


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=HERE, **kwargs)

    def log_message(self, fmt, *args):
        # 静默静态资源日志，只保留错误
        pass

    # ------------------------------------------------------------ 图像生成

    def handle_image(self):
        """生成菜品图并下载成 dataURL 返回，避免前端受 URL 过期与跨域影响。"""
        try:
            length = int(self.headers.get("Content-Length") or 0)
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception:
            self._json(400, {"error": "请求体格式错误"})
            return

        prompt = (payload.get("prompt") or "").strip()
        if not prompt:
            self._json(400, {"error": "缺少 prompt"})
            return

        cfg = load_config()

        # 图像可以用独立的服务商（例如文本走智谱、图像走硅基流动的免费 Kolors）
        key = (cfg.get("image_api_key") or cfg.get("api_key") or "").strip()
        if not key:
            self._json(400, {"error": "服务端还没配置 Key：请编辑 web/config.local.json"})
            return

        base = (cfg.get("image_base_url") or cfg.get("base_url")
                or "https://open.bigmodel.cn/api/paas/v4").rstrip("/")
        model = payload.get("model") or cfg.get("image_model") or "cogview-3-flash"
        size = payload.get("size") or "1024x1024"


        if "siliconflow" in base:
            payload_body = {
                "model": model,
                "prompt": prompt,
                "image_size": size,
                "batch_size": 1,
                "num_inference_steps": 30,
                "guidance_scale": 4.0,
            }
        else:
            payload_body = {"model": model, "prompt": prompt, "size": size}
        body = json.dumps(payload_body).encode("utf-8")
        req = urllib.request.Request(
            base + "/images/generations",
            data=body,
            headers={
                "Content-Type": "application/json; charset=utf-8",
                "Authorization": "Bearer " + key,
                "Accept": "application/json",
            },
            method="POST",
        )

        try:
            with urllib.request.urlopen(req, timeout=180) as resp:
                j = json.loads(resp.read().decode("utf-8", "ignore"))
        except urllib.error.HTTPError as e:
            detail = ""
            try:
                detail = e.read().decode("utf-8", "ignore")
            except Exception:
                pass
            friendly = {
                401: "API Key 无效",
                403: "没有该图像模型的权限",
                404: "图像模型名不对",
                429: "生成太频繁或额度用完，稍后再试",
            }.get(e.code, "图像生成失败")
            msg = friendly + "（HTTP %d）" % e.code
            try:
                inner = (json.loads(detail).get("error") or {}).get("message")
                if inner:
                    msg += "：" + str(inner)[:120]
            except Exception:
                pass
            self._json(502, {"error": msg})
            return
        except Exception as e:
            self._json(502, {"error": "网络错误：%s" % e})
            return

        url = None
        for keyname in ("data", "images"):
            try:
                seq = j.get(keyname) or []
                if seq:
                    url = seq[0].get("url")
                    if url:
                        break
            except Exception:
                pass
        if not url:
            self._json(502, {"error": "图像接口没有返回图片地址"})
            return

        # 图片 URL 带签名且会过期，立刻下载转成 dataURL 交给前端
        try:
            with urllib.request.urlopen(url, timeout=90) as resp:
                raw = resp.read()
        except Exception as e:
            self._json(502, {"error": "图片下载失败：%s" % e})
            return

        import base64
        mime = "image/jpeg"
        data_url = "data:%s;base64,%s" % (mime, base64.b64encode(raw).decode("ascii"))
        self._json(200, {
            "dataUrl": data_url,
            "bytes": len(raw),
            "model": model,
            "watermarked": "watermark" in url,
        })

    # ------------------------------------------------------------ 网络搜图

    def handle_search_image(self):
        """用 Bing 图片搜索找菜品图，返回带缩略图的候选列表。

        走服务端有两个原因：Bing 搜索页无 CORS 头，且原图多站有防盗链，
        浏览器端既解析不了 HTML 也拿不到图。
        """
        try:
            length = int(self.headers.get("Content-Length") or 0)
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception:
            self._json(400, {"error": "请求体格式错误"})
            return

        query = (payload.get("query") or "").strip()
        if not query:
            self._json(400, {"error": "缺少 query"})
            return
        want = max(1, min(int(payload.get("count") or 12), 24))
        page = max(0, min(int(payload.get("page") or 0), 5))

        try:
            hits = self._bing_search(query, want * 2, offset=1 + page * 35)
        except Exception as e:
            self._json(502, {"error": "搜索失败：%s" % e})
            return

        if not hits:
            self._json(200, {"query": query, "items": [], "page": page,
                             "note": "这一页没有更多结果了"})
            return

        # 并行拉缩略图（turl 在 Bing 自家 CDN 上，不受防盗链影响）
        def grab(hit):
            try:
                raw = http_get(hit["turl"], timeout=15, max_bytes=1024 * 1024)
                if len(raw) < 1200:
                    return None
                mime = "png" if raw[:4] == b"\x89PNG" else "jpeg"
                hit["thumb"] = "data:image/%s;base64,%s" % (
                    mime, base64.b64encode(raw).decode("ascii"))
                return hit
            except Exception:
                return None

        with ThreadPoolExecutor(max_workers=8) as ex:
            items = [x for x in ex.map(grab, hits) if x]

        self._json(200, {"query": query, "items": items[:want], "page": page})

    def _bing_search(self, query, limit, offset=1):
        """解析 Bing 图片搜索页，返回 [{murl,turl,purl,title}]"""
        url = ("https://cn.bing.com/images/search?q="
               + urllib.parse.quote(query) + "&form=HDRSC2&first=%d" % offset)
        req = urllib.request.Request(url, headers={
            "User-Agent": BROWSER_UA,
            "Accept-Language": "zh-CN,zh;q=0.9",
            "Accept": "text/html,application/xhtml+xml",
        })
        with urllib.request.urlopen(req, timeout=25) as r:
            html_text = r.read().decode("utf-8", "ignore")

        out = []
        seen = set()
        for block in re.findall(r'm="(\{&quot;.*?\})"', html_text):
            try:
                meta = json.loads(unescape(block))
            except Exception:
                continue

            murl = (meta.get("murl") or "").strip()
            turl = (meta.get("turl") or "").strip()
            purl = (meta.get("purl") or "").strip()
            if not murl or not turl or murl in seen:
                continue
            if any(h in purl.lower() for h in WATERMARK_HOSTS):
                continue

            seen.add(murl)
            out.append({
                "murl": murl,
                "turl": turl,
                "purl": purl,
                "title": (meta.get("t") or "")[:80],
            })
            if len(out) >= limit:
                break
        return out

    def handle_fetch_image(self):
        """把选中的原图下载下来并转 dataURL，绕开防盗链与跨域。"""
        try:
            length = int(self.headers.get("Content-Length") or 0)
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception:
            self._json(400, {"error": "请求体格式错误"})
            return

        url = (payload.get("url") or "").strip()
        if not url.startswith(("http://", "https://")):
            self._json(400, {"error": "图片地址不合法"})
            return

        referer = payload.get("referer") or "https://cn.bing.com/"
        try:
            raw = http_get(url, referer=referer, timeout=30)
        except Exception:
            # 部分站点只认自己的来源页，换 Bing 再试一次
            try:
                raw = http_get(url, referer=None, timeout=30)
            except Exception as e:
                self._json(502, {"error": "图片下载失败：%s" % e})
                return

        if len(raw) < 4000:
            self._json(502, {"error": "图片太小或不是有效图片"})
            return

        mime = "png" if raw[:4] == b"\x89PNG" else "jpeg"
        if raw[:4] == b"GIF8":
            mime = "gif"
        elif raw[:4] == b"RIFF" and raw[8:12] == b"WEBP":
            mime = "webp"

        self._json(200, {
            "dataUrl": "data:image/%s;base64,%s" % (mime, base64.b64encode(raw).decode("ascii")),
            "bytes": len(raw),
        })

    # ------------------------------------------------------------ 工具

    def _json(self, code, obj):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    # ------------------------------------------------------------ GET

    def do_GET(self):
        if self.path == "/api/status":
            cfg = load_config()
            self._json(200, {
                "proxy": True,
                "configured": bool(cfg.get("api_key")),
                "model": cfg.get("model", ""),
                "baseUrl": cfg.get("base_url", ""),
            })
            return
        super().do_GET()

    # ------------------------------------------------------------ POST

    def do_POST(self):
        if self.path == "/api/image":
            self.handle_image()
            return
        if self.path == "/api/search-image":
            self.handle_search_image()
            return
        if self.path == "/api/fetch-image":
            self.handle_fetch_image()
            return
        if self.path != "/api/chat":
            self.send_error(404)
            return

        try:
            length = int(self.headers.get("Content-Length") or 0)
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception:
            self._json(400, {"error": "请求体格式错误"})
            return

        cfg = load_config()
        key = (cfg.get("api_key") or "").strip()
        if not key:
            self._json(400, {
                "error": "服务端还没配置 Key：请编辑 web/config.local.json 填写 api_key"
            })
            return

        base = (cfg.get("base_url") or "https://open.bigmodel.cn/api/paas/v4").rstrip("/")
        model = payload.get("model") or cfg.get("model") or "glm-4-flash"

        body = {
            "model": model,
            "messages": [{"role": "user", "content": payload.get("prompt", "")}],
            "temperature": 0.7,
            "max_tokens": int(payload.get("max_tokens", 3000)),
            "stream": False,
        }

        req = urllib.request.Request(
            base + "/chat/completions",
            data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
            headers={
                "Content-Type": "application/json; charset=utf-8",
                "Authorization": "Bearer " + key,
                "Accept": "application/json",
            },
            method="POST",
        )

        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                raw = resp.read().decode("utf-8", "ignore")
        except urllib.error.HTTPError as e:
            detail = ""
            try:
                detail = e.read().decode("utf-8", "ignore")
            except Exception:
                pass
            friendly = {
                401: "API Key 无效或没有该模型权限",
                403: "API Key 无权限",
                404: "接口地址或模型名不对",
                429: "免费额度限流了，等一会儿再试",
            }.get(e.code, "请求失败")
            msg = friendly + "（HTTP %d）" % e.code
            try:
                j = json.loads(detail)
                inner = (j.get("error") or {}).get("message") or j.get("msg")
                if inner:
                    msg += "：" + str(inner)[:120]
            except Exception:
                pass
            self._json(502, {"error": msg})
            return
        except Exception as e:
            self._json(502, {"error": "网络错误：%s" % e})
            return

        try:
            j = json.loads(raw)
            msg = ((j.get("choices") or [{}])[0].get("message") or {})
            content = (msg.get("content") or "").strip()
            reasoning = (msg.get("reasoning_content") or "").strip()
            if not content:
                self._json(502, {
                    "error": "模型返回了空内容" + (
                        "：这个模型默认开启深度思考，思考过程占满了输出额度，换 glm-4-flash 即可"
                        if reasoning else "，可能是模型名不对或额度用尽"
                    )
                })
                return
            self._json(200, {"content": content, "usage": j.get("usage"), "model": model})
        except Exception:
            self._json(502, {"error": "无法解析模型返回内容"})


def main():
    cfg = load_config()
    os.chdir(HERE)

    print("=" * 52)
    print("  我的菜谱 · 本地预览")
    print("=" * 52)
    print("  地址： http://127.0.0.1:%d" % PORT)
    if cfg.get("api_key"):
        print("  AI：   已启用（%s / %s）" % (cfg.get("model"), "Key 已配置"))
    else:
        print("  AI：  未配置，请编辑 web/config.local.json")
    print("  按 Ctrl+C 停止")
    print("=" * 52)
    sys.stdout.flush()

    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")


if __name__ == "__main__":
    main()
