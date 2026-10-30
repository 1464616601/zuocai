@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo.
echo  ==========================================
echo    我的菜谱 · 网页预览版
echo  ==========================================
echo.

where python >nul 2>nul
if errorlevel 1 (
    echo  [!] 未检测到 python，改为直接打开单文件版本。
    echo      注意：单文件版本没有本地代理，AI 功能需要自己填 API Key。
    echo.
    start "" "preview.html"
    pause
    exit /b 0
)

if not exist "config.local.json" (
    echo  [!] 未找到 config.local.json，AI 功能将不可用。
    echo      复制 config.example.json 为 config.local.json 并填入 api_key 即可启用。
    echo.
)

echo  正在启动本地服务 http://127.0.0.1:8765
echo  AI 请求由本服务转发，浏览器不接触 API Key。
echo  关闭本窗口即可停止服务。
echo.

start "" "http://127.0.0.1:8765/index.html"
python serve.py

endlocal
