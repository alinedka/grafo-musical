@echo off
REM Compila e executa o Compositor Musical com Grafos
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\*.java
if errorlevel 1 (
    echo Erro na compilacao.
    pause
    exit /b 1
)
java -cp out CompositorApp
