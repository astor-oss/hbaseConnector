#!/bin/bash
set -x

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

ROOT=$(dirname $DIR)

rsync --delete -avrz -e ssh \
    --exclude "git" \
    --exclude ".idea" \
    $ROOT/ huzongxing@192.168.1.81:/mnt/huzongxing/shc

