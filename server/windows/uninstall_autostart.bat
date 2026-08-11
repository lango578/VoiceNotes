@echo off
chcp 65001 >nul
schtasks /Delete /TN VoiceNotesServer /F >nul 2>&1
echo 已移除开机自启计划任务 VoiceNotesServer。
pause
