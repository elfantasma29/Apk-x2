#!/bin/sh
arm-linux-gnueabihf-g++ -std=c++17 -O1 -rdynamic -static-libgcc -static-libstdc++ -I ../inc -o ivona_server ivona_server.cpp shim32c.cpp shim_extra.cpp shim_dlopen.cpp shim_stat.cpp -ldl -lpthread
