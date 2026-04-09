# Contributing Guidelines

Thank you for looking for ways to help on Nexus Chat Android!

This document tries to outline some conventions that may not be obvious
and aims to give a good starting point to new contributors.


## Reporting Bugs

If you found a bug, [report it on Github](https://github.com/ashlydev99/NexusChat/issues).

Please search both open and closed issues to make sure your bug report is not a duplicate.

## Proposing Features

If you have a feature request,
contact ashlydev99@gmail.com.


## Rough UX Philosophy

Some rough ideas, that may be helpful when thinking about how to enhance things:

- Work hard to avoid options and up-front choices.
  Thinking about concrete user stories may help on that.
- Avoid to speak about keys and other hard to understand things in the primary UI.
- The app shall work offline as well as with bad network.
- Users do not read (much).
- Consistency matters.
- Offer only things that are highly useful to many people in primary UI.
  If really needed, bury other things eg. in some menus.
- The app should be for the many, not for the few.


## Contributing Code

To contribute code,
[open a Pull Request](https://github.com/ashlydev99/NexusChat/pulls).

If you have write access to the repository,
push a branch named `<username>/<feature>`
so it is clear who is responsible for the branch,
and open a PR proposing to merge the change.
Otherwise fork the repository and create a branch in your fork.

Please add a meaningful description to your PR
so that reviewers get an idea about what the modifications are supposed to do.

A meaningful PR title is helpful for [updating `CHANGELOG.md` on releases](./RELEASE.md)
(CHANGELOG.md is updated manually
to only add things that are at least roughly understandable by the end user)

If the changes affect the user interface,
screenshots are very helpful,
esp. before/after screenshots.


### Coding Conventions

Project language is Java.

Code formatting is enforced via [Spotless](https://github.com/diffplug/spotless) Gradle plugin.
Auto-format all files by running `./gradlew spotlessApply` before opening a PR.
CI will fail if files are not formatted correctly so make sure to run the formatter before pushing.

If you do a PR fixing a bug or adding a feature, do not refactor or rename things in the same PR
to make the diff small and the PR easy to review.

### Merging Conventions

PR are merged usually to the branch `main` from which [releases](./RELEASE.md) are done.

As a default, do a `git rebase main` in case feature branches and `main` differ too much.

Once a PR has an approval, unless stated otherwise, it can be merged by the author.
A PR may be approved but postponed to be merged eg. because of an ongoing release.

To ensure the correct merge merge strategy, merging left up to the PR author:

- Usually, PR are squash-merged
  as UI development often results in tiny tweak commits that are not that meaningful on their own.
- If all commits are meaningful and have a well-written description,
  they can be rebased-merged.

If you do not have write access to the repository,
you may leave a note in the PR about the desired merge strategy.