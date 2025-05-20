cd ../ui
pwd
yarn install
yarn build
echo "Done building UI, copying the UI files to server"
cd ..
pwd
rm -rf server-lite/src/main/resources/static
mv ui/build/ server-lite/src/main/resources/static