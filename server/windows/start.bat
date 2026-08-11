@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ============================================
echo   VoiceNotes 后端（Windows 便携版）
echo   本机访问:   http://127.0.0.1:8000
echo   手机访问:   http://局域网IP:8000  （ipconfig 查询）
echo   停止服务:   关闭本窗口
echo ============================================
"%~dp0python\python.exe" "%~dp0run_server.py"
pause
