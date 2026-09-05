#!/bin/bash

# Adapted from vendor/google_clockwork/packages/SystemUI/daggervis/visualize_dagger_component.sh
# Usage: visualize_dagger_component.sh output_file component_name [filter]
# Example: visualize_dagger_component.sh ~/CarSysUIComponent.svg CarSysUIComponent Keyguard
if [ -z "$1" ]; then
  echo "Error: please specify an output file path. Example: \"visualize_dagger_component.sh ~/CarSysUIComponent.svg CarSysUIComponent Keyguard\""
  exit 1
fi

if [ -z "$2" ]; then
  echo "Error: please specify a dagger component name. Example: \"visualize_dagger_component.sh CarSysUIComponent Keyguard\""
  exit 1
fi

if [ -z "$ANDROID_BUILD_TOP" ]; then
  echo "Error: cannot find ANDROID_BUILD_TOP. Please go to Android root folder and run \". build/envsetup.sh\" and lunch a target."
  exit 1
fi

ARTIFACTS_FOLDER=$ANDROID_BUILD_TOP/out/target/common/obj/JAVA_LIBRARIES/CarSystemUI-core-daggervis_intermediates
CLASSES_FILE=$ARTIFACTS_FOLDER/classes.jar
if [ ! -f $CLASSES_FILE ]; then
  echo "Error: cannot find CarSystemUI-core-daggervis artifacts. Please run \"m CarSystemUI-core-daggervis\" first."
  exit 1
fi

DOT_FOLDER=$ARTIFACTS_FOLDER/dot
rm -rf $DOT_FOLDER
mkdir $DOT_FOLDER

echo "Unzipping dot files ..."
unzip -d $DOT_FOLDER/ $CLASSES_FILE "*.dot" > /dev/null

DOT_FILE=$DOT_FOLDER/$2.dot
if [ ! -f $DOT_FILE ]; then
  echo "Error: can't find file $DOT_FILE. Did you forget to rebuild CarSystemUI-core-daggervis?"
  exit 1
fi

echo "Parsing $DOT_FILE"
PARSED_DOT_FILE=$DOT_FOLDER/$2_parsed.dot
$ANDROID_BUILD_TOP/packages/apps/Car/SystemUI/daggervis/parser.py $DOT_FILE $PARSED_DOT_FILE $3
if [[ $? -ne 0 ]]; then
  exit 1
fi

echo "Visualizing $PARSED_DOT_FILE"
# dot -v -T svg $PARSED_DOT_FILE > $1
dot -v -T svg -Ktwopi -Goverlap=prism -Gsplines=true $PARSED_DOT_FILE > $1

