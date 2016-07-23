#!/bin/bash
#
# https://groups.google.com/forum/#!topic/go-mobile/ZstjAiIFrWY
#

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

GOPATH=$DIR

if [ ! -e "$GOPATH/pkg/gomobile" ]; then
  gomobile init || exit 1
fi

gomobile bind -o "$DIR/libtorrent.aar" github.com/axet/libtorrent || exit 1
