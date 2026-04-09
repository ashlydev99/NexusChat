#!/usr/bin/env bash

VERSION=$1

if [ -z "$VERSION" ]; then
    echo "this script uploads release-ready apk and symbols to download.nexus.chat/android"
    echo "- for showing up on get.nexus.chat"
    echo "  you still need to change nexuschat-pages/_includes/download-boxes.html"
    echo "- the script does not upload things to gplay or other stores."
    echo ""
    echo "usage: ./scripts/upload-release.sh <VERSION>"
    exit
fi
if [[ ${VERSION:0:1} == "v" ]]; then
    echo "VERSION must not begin with 'v' here."
    exit
fi

cd gplay/release
APK="nexuschat-gplay-release-$VERSION.apk"
ls -l $APK
read -p "upload this apk and belonging symbols to download.nexus.chat/android? ENTER to continue, CTRL-C to abort."

# you need the private SSH key of the jekyll user; you can find it in this file:
# https://github.com/hpk42/otf-nexuschat/blob/master/secrets/nexus.chat
# It is protected with [git-crypt](https://www.agwa.name/projects/git-crypt/) -
# after installing it, you can decrypt it with `git crypt unlock`.
# If your key isn't added to the secrets, and you know some of the team in person,
# you can ask on irc #nexuschat for access.
# Add the key to your `~/.ssh/config` for the host, or to your ssh-agent, so rsync is able to use it)
rsync --progress $APK jekyll@download.nexus.chat:/var/www/html/download/android/

cd ../..
SYMBOLS_ZIP="$APK-symbols.zip"
rm $SYMBOLS_ZIP
zip -r $SYMBOLS_ZIP obj
ls -l $SYMBOLS_ZIP
rsync --progress $SYMBOLS_ZIP jekyll@download.nexus.chat:/var/www/html/download/android/symbols/

echo "upload done."
echo ""
echo "and now: here is Nexus Chat $VERSION:"
echo "- 🍋 https://download.nexus.chat/android/nexuschat-gplay-release-$VERSION.apk (google play candidate, overwrites existing installs, should keep data)"
echo ""
