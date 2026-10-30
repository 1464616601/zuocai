"""把 index.html + app.css + parser.js + games.js + app.js 内联成单文件 preview.html。

改动任一源文件后执行：python rebuild.py
"""
import io
import os

HERE = os.path.dirname(os.path.abspath(__file__))


def read(name):
    with io.open(os.path.join(HERE, name), encoding="utf-8") as f:
        return f.read()


def main():
    html = read("index.html")
    css = read("app.css")
    seeds = read("default-recipes.js")
    parser = read("parser.js")
    games = read("games.js")
    app = read("app.js")

    single = html.replace(
        '<link rel="stylesheet" href="app.css">',
        "<style>\n" + css + "\n</style>",
    )
    single = single.replace(
        '<script src="default-recipes.js"></script>\n'
        '<script src="parser.js"></script>\n<script src="games.js"></script>\n'
        '<script src="app.js"></script>',
        "<script>\n" + seeds + "\n</script>\n"
        "<script>\n" + parser + "\n</script>\n"
        "<script>\n" + games + "\n</script>\n"
        "<script>\n" + app + "\n</script>",
    )
    single = single.replace("<title>我的菜谱</title>", "<title>我的菜谱 · 预览</title>")

    if "window.RecipeParser" not in single:
        raise SystemExit("parser.js 未正确内联")
    if "recipeText" not in single:
        raise SystemExit("app.js 未正确内联")
    if "window.Games" not in single:
        raise SystemExit("games.js 未正确内联")

    out = os.path.join(HERE, "preview.html")
    with io.open(out, "w", encoding="utf-8") as f:
        f.write(single)
    print("preview.html 已生成：{} 字节".format(len(single)))


if __name__ == "__main__":
    main()
