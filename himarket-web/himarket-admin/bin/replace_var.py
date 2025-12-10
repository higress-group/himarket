#!/usr/bin/python
# -*- coding: utf-8 -*-
from jinja2 import Template
import sys
import os
import traceback
import json


def readFileTrimspace(filepath):
    try:
        result = list()
        is_yaml = 'yaml' in filepath or 'yml' in filepath
        if os.path.isfile(filepath):
            f = open(filepath, 'r')
            for line in f.readlines():
                line = (line.strip('\n') if is_yaml else line.strip())
                if not len(line):
                    continue
                result.append(line)
        return "\n".join(result)
    except:
        traceback.print_exc()
        return ""

def readFile(filepath):
    try:
        if not os.path.isfile(filepath):
            print("The input [%s] is not a file" % filepath)
            return ""
        if os.path.isfile(filepath):
            try:
                f = open(filepath, 'r')
                return f.read()
            finally:
                if f:
                    f.close()
    except:
        traceback.print_exc()
        return ""


def writeFile(filepath, content):
    try:
        open(filepath, 'w+').write(content)
    except:
        traceback.print_exc()


def dockerenv2json():
    envJson = {}
    with open('/.dockerenv') as ifs:
        data = ifs.read()
    assert len(data) > 2, 'invalid dockerenv'
    envlist = json.loads(data)
    for env in envlist:
        indexOfEqual = env.index('=')
        if indexOfEqual > 0:
            key = env[0: indexOfEqual]
            value = env[indexOfEqual + 1:]
            envJson[key] = value
    return envJson


def createVarMap(filepath):
    map = os.environ
    # add more param to map
    return map


def main():
    filepath = sys.argv[1] if len(sys.argv) > 1 else ''
    t = Template(readFile(filepath))
    map = createVarMap(filepath)
    ret = t.render(map)
    writeFile(filepath, ret)


if __name__ == "__main__":
    reload(sys)
    sys.setdefaultencoding('utf-8')
    main()