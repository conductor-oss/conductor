#
#  Copyright 2017 Netflix, Inc.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
# 
from setuptools import setup

setup(
  name = 'conductor',
  packages = ['conductor'], # this must be the same as the name above
  version = '1.0.0',
  description = 'Conductor python client',
  author = 'Viren Baraiya',
  author_email = 'vbaraiya@netflix.com',
  url = 'https://github.com/netflix/conductor',
  download_url = 'https://github.com/Netflix/conductor/releases',
  keywords = ['conductor'],
  license = 'Apache 2.0',
  install_requires = [
    'requests',
  ],
  classifiers = [
    'Development Status :: 4 - Beta',
    'Intended Audience :: Developers',
    'Intended Audience :: System Administrators',
    'License :: OSI Approved :: Apache Software License',
    'Programming Language :: Python :: 2.7',
    'Topic :: Workflow',
    'Topic :: Microservices',
    'Topic :: Orchestration',
    'Topic :: Internet',
    'Topic :: Software Development :: Libraries :: Python Modules',
    'Topic :: System :: Networking'
  ],
)
