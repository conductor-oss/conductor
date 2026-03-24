cd ../ui
pwd
export REACT_APP_ENABLE_ERRORS_INSPECTOR=true
yarn install
yarn build
echo "Done building UI, copying the UI files to server"
cd ..
pwd
rm -rf server-lite/src/main/resources/static
mv ui/build/ server-lite/src/main/resources/static