#!/usr/bin/env python3
#
# Copyright (c) 2026 Contributors to the Eclipse Foundation.
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
# SPDX-License-Identifier: EPL-2.0
#
# Contributors:
#
"""Lists the version of each API package of a checkout of the legacy build.

Usage: package-versions.py <directory>

Prints a line package=version for every package-info.java below the directory,
for the versions parameter of link-index.xsl. The version is that of the
@Version annotation of the package. Most annotations give a literal, but the
specifications give it as a constant of the package, optionally with a
suffix, such as CDIConstants.CDI_SPECIFICATION_VERSION + ".1", so those are
resolved from the constants of the checkout. A package whose version cannot be
resolved is reported on the standard error and left out.
"""
import os
import re
import sys

CONSTANT = re.compile(r'\bString\s+(\w+_VERSION)\s*=\s*"([^"]*)"')
PACKAGE = re.compile(r'^package\s+([\w.]+)\s*;', re.M)
VERSION = re.compile(r'@Version\(\s*([^()]*?)\s*\)')


def read(path):
    with open(path, encoding='utf-8', errors='replace') as f:
        return f.read()


def scan(root):
    constants = {}
    infos = []
    for directory, _, files in os.walk(root):
        for name in files:
            if not name.endswith('.java'):
                continue
            path = os.path.join(directory, name)
            text = read(path)
            for m in CONSTANT.finditer(text):
                constants.setdefault(m.group(1), set()).add(m.group(2))
            if name == 'package-info.java':
                package = PACKAGE.search(text)
                version = VERSION.search(text)
                if package and version:
                    infos.append((package.group(1), version.group(1), path))
    return constants, infos


def resolve(expression, constants):
    result = ''
    for term in expression.split('+'):
        term = term.strip()
        if term.startswith('"') and term.endswith('"'):
            result += term[1:-1]
        else:
            # A constant of another class is named with its class
            values = constants.get(term.split('.')[-1], set())
            if len(values) != 1:
                return None
            result += next(iter(values))
    return result


def main(argv):
    if len(argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    constants, infos = scan(argv[1])
    versions = {}
    for package, expression, path in sorted(infos):
        version = resolve(re.sub(r'\s+', ' ', expression), constants)
        if version is None:
            print('No version for %s: @Version(%s) in %s' % (package, expression, path), file=sys.stderr)
        elif versions.get(package, version) != version:
            print('Different versions of %s: %s and %s' % (package, versions[package], version), file=sys.stderr)
        else:
            versions[package] = version
    for package in sorted(versions):
        print('%s=%s' % (package, versions[package]))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
