#
# Copyright (c) 2023 Airbyte, Inc., all rights reserved.
#


import sys

from airbyte_cdk.entrypoint import launch
from source_scaffold_source_python import SourceScaffoldSourcePython


def run():
    source = SourceScaffoldSourcePython()
    launch(source, sys.argv[1:])
