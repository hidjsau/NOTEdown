NOTES - Jetpack Compose Android app (WebView host for your notes_v32.html)

HOW TO UPLOAD (phone friendly)
1. Extract the zip. Upload ALL files to the ROOT of your GitHub repo.
   File names look like  app_src_main_res_values_strings.xml
   meaning               app/src/main/res/values/strings.xml
   (restructure.sh builds the folders automatically during the build.)
2. ONE manual step - GitHub only runs workflows from .github/workflows/ :
   Repo -> Add file -> Create new file -> in the name box type
       .github/workflows/build.yml
   Paste the contents of dotgithub_workflows_build.yml and commit.
   (You can delete dotgithub_workflows_build.yml afterwards, it is only a copy.)
3. Actions tab -> "Build Android APK" -> Run workflow -> pick debug or release.
4. When it finishes, open the run -> Artifacts -> download the APK.
   Unzip it and install the .apk on your phone.

TO UPDATE YOUR NOTES APP LATER
Replace app_src_main_assets_index.html with your new HTML (same file name) and re-run.

NOTES
- Downloads/exports from the page are saved to your phone's Downloads folder.
- CDN libraries (KaTeX, PDF libs, fonts) are cached on first online launch, then work offline.
- Android 10+ required (minSdk 29).
