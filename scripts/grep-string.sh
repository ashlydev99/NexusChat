TEXT=$1

if [ -z "$TEXT" ]; then
    echo "search for a given string key in this repo"
    echo "and in ../nexuschat-ios, ../nexuschat-desktop, ../nexuschat-core-rust/nexuschat-jsonrpc, ../nexustouch"
    echo "usage: ./scripts/grep-string.sh <STRING-KEY>"
    exit
fi

echo "==================== ANDROID USAGE ===================="
grep --exclude={*.apk,*.a,*.o,*.so,strings.xml,*symbols.zip} --exclude-dir={.git,.gradle,obj,release,.idea,build,nexuschat-core-rust} -ri $TEXT .

echo "==================== IOS USAGE ===================="
grep --exclude=*.strings* --exclude-dir={.git,libraries,Pods,nexuschat-ios.xcodeproj,nexuschat-ios.xcworkspace} -ri $TEXT ../nexuschat-ios/

echo "==================== DESKTOP USAGE ===================="
grep --exclude-dir={.cache,.git,html-dist,node_modules,_locales} -ri $TEXT ../nexuschat-desktop/

echo "==================== JSONRPC USAGE ===================="
grep  --exclude-dir={.git} -ri $TEXT ../chatmail/core/nexuschat-jsonrpc

echo "==================== UBUNTU TOUCH USAGE ===================="
grep  --exclude-dir={.git} -ri $TEXT ../nexustouch/

