@echo off
title SleathCam1 - Admin Web Panel Live Deployment
color 0b

echo =====================================================================
echo    SLEATHCAM1 / CALCULATOR VAULT - WEB ADMIN PANEL DEPLOYMENT
echo =====================================================================
echo  Project ID: sleathcam1
echo  Live URL will be: https://sleathcam1.web.app
echo                    https://sleathcam1.firebaseapp.com
echo =====================================================================
echo.

echo Choose an action:
echo  [1] Deploy Web Admin Panel Live to Firebase Hosting (Recommended)
echo  [2] Deploy Firestore Rules + Storage Rules
echo  [3] Full Deploy (Web Admin + Rules)
echo  [4] Run Local Preview Server (Test on your computer)
echo  [5] Exit
echo.

set /p choice="Enter your choice (1-5): "

if "%choice%"=="1" goto deploy_hosting
if "%choice%"=="2" goto deploy_rules
if "%choice%"=="3" goto deploy_all
if "%choice%"=="4" goto test_local
if "%choice%"=="5" goto end_script
goto invalid_choice

:deploy_hosting
echo.
echo [*] Deploying Web Admin Panel (admin-web) to Firebase Hosting...
call npx -y firebase-tools deploy --only hosting --project sleathcam1
if %errorlevel% neq 0 (
    echo.
    echo [!] If you are not logged in, please run:
    echo     npx firebase-tools login
    echo Then run this script again.
) else (
    echo.
    echo [SUCCESS] Web Admin Panel is now LIVE!
    echo Visit: https://sleathcam1.web.app
    echo    or: https://sleathcam1.firebaseapp.com
)
goto finish

:deploy_rules
echo.
echo [*] Deploying Firestore Security Rules and Storage Rules...
call npx -y firebase-tools deploy --only firestore:rules,storage --project sleathcam1
if %errorlevel% neq 0 (
    echo [!] Rules deployment failed. Check login with: npx firebase-tools login
) else (
    echo [SUCCESS] Firestore and Storage security rules are active!
)
goto finish

:deploy_all
echo.
echo [*] Deploying Web Panel + Security Rules...
call npx -y firebase-tools deploy --project sleathcam1
if %errorlevel% neq 0 (
    echo [!] Deployment failed. Check login with: npx firebase-tools login
) else (
    echo [SUCCESS] Everything deployed successfully!
    echo Live URL: https://sleathcam1.web.app
)
goto finish

:test_local
echo.
echo [*] Starting local preview server...
echo Open your browser at http://localhost:5000
call npx -y serve admin-web -p 5000
goto finish

:invalid_choice
echo [!] Invalid selection. Please run again.
goto finish

:finish
echo.
echo =====================================================================
echo  Press any key to exit.
echo =====================================================================
pause >nul

:end_script
