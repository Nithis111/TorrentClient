#!/bin/bash
#
# https://groups.google.com/forum/#!topic/go-mobile/ZstjAiIFrWY
#

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

go get -u github.com/axet/libtorrent || exit 1

gomobile bind -o "$DIR/libtorrent.aar" github.com/axet/libtorrent || exit 1
