# jute.clj

A Clojure implementation of the [JUTE template
language](https://github.com/HealthSamurai/jute.js).

[![Build Status](https://travis-ci.org/HealthSamurai/jute.clj.svg?branch=master)](https://travis-ci.org/HealthSamurai/jute.clj)

# Reference

## Introduction

JUTE stands for JSON Uniform Templates and it's a small language to
describe JSON documents transformations. JUTE templates are JSON
documents itself. It's safe to evaluate user-provided JUTE templates,
there is no way for a template to currupt a runtime environment.

## Few words about YAML

JSON format wasn't designed for ease of use by human beings, it's
relatively hard to write JSON by hands. That's why JUTE's primary
format is [YAML](https://yaml.org/), which is much easier to read and
write, thanks to its clean syntax and indentation-based nesting. Don't
be confused with it, YAML and JSON are interchangeable, and there are
even [online conversion tools](https://www.json2yaml.com/) beetween them.

## An example

Let's say we have a document describing a book:

```yaml
---
book:
  author:
    name: M. Soloviev
    title: PHD
  title: Approach to Cockroach
  chapters:
  - type: preface
    content: A preface chapter
  - type: content
    content: Chapter 1
  - type: content
    content: Chapter 2
  - type: content
    content: Chapter 3
  - type: afterwords
    content: Afterwords
```

## License

Copyright © 2019 Health Samurai Team

Distributed under the MIT License.
