#!/bin/python
# Converts fastlane into json
import sys

version = sys.argv[1]

filePath = "fastlane/metadata/android/en-US/changelogs/" + version + ".txt"

file = open(filePath, "r")
lines = file.readlines()

for line in lines:
    print("\"" + line.replace("\n", "") + "\",")
