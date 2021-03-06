#!/bin/bash

if [[ $EUID -ne 0 ]]; then
   echo "This script must be run as root" 1>&2
   exit 1
fi

apt-get update
apt-get upgrade -y

# python deps

apt-get install python-pexpect

# BlueZ building and runtime
apt-get install automake build-essential libtool glib2.0 libdbus-1-dev libudev-dev libical-dev libreadline-dev -y

# SDL
apt-get install libsdl-dev -y


# XBox controller

apt-get install xboxdrv jstest-gtk -y


# deamon tools

apt-get install daemontools daemontools-run -y


# mjpgstreamer for Camera

apt-get install libv4l-dev libjpeg8-dev imagemagick cmake -y

# nodered deps 
apt-get install python-rpi.gpio python-dev -y


# Scratch integration

apt-get install python-pip -y
sudo pip install websocket-client scratchpy

# Rub (e.g. Sonic Pi) integration support

sudo gem install websocket-client-simple
