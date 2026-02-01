cd ui
pwd
export REACT_APP_ENABLE_ERRORS_INSPECTOR=true
export REACT_APP_MONACO_EDITOR_USING_CDN=false
yarn install
yarn build
echo "Done building UI, copying the UI files to server"
cd ..
pwd
rm -rf server-lite/src/main/resources/static/*
rm -rf server/src/main/resources/static/*
cp -r ui/build/ server-lite/src/main/resources/static
cp -r ui/build/ server/src/main/resources/static