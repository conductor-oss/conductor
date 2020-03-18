# Contributing to Conductor

We are following the Gitflow workflow. The active development branch is [dev](https://github.com/Netflix/conductor/tree/dev), the stable branch is [master](https://github.com/Netflix/conductor/tree/master).

Contributions will be accepted to the [dev](https://github.com/Netflix/conductor/tree/dev) only.

## How to provide a patch for a new feature

1. If it is a major feature, please create an [Issue]( https://github.com/Netflix/conductor/issues ) and discuss with the project leaders.

2. If in step 1 you get an acknowledge from the project leaders, use the
   following procedure to submit a patch:

    a. Fork conductor on github ( http://help.github.com/fork-a-repo/ )

    b. Create a topic branch (git checkout -b my_branch)

    c. Push to your branch (git push origin my_branch)

    d. Initiate a pull request on github ( http://help.github.com/en/articles/creating-a-pull-request/ )

    e. Done :)

For minor fixes just open a pull request to the [dev]( https://github.com/Netflix/conductor/tree/dev ) branch on Github.

## License

By contributing your code, you agree to license your contribution under the terms of the APLv2: https://github.com/Netflix/conductor/blob/master/LICENSE

All files are released with the Apache 2.0 license.

If you are adding a new file it should have a header like this:

```
/**
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
```

## Questions

If you have questions or want to report a bug please create an [Issue]( https://github.com/Netflix/conductor/issues ) or chat with us on [![Dev chat at https://gitter.im/Netflix/dynomite](https://badges.gitter.im/netflix-conductor/community.svg)](https://gitter.im/netflix-conductor/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
