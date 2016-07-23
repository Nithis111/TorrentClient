#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

GOPATH=$DIR

go get -u github.com/axet/libtorrent || exit 1
