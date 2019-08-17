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
book:
  author:
    name: M. Soloviev
    title: PHD
    gender: m
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

And for some case we need to convert it into a slightly different
format:

```yaml
type: book
author: M. Soloviev
title: Approach to Cockroach
content:
- Chapter 1
- Chapter 2
- Chapter 3
```

Attentive reader will notice that we're going to discard preface and
afterwords as well as minor author information keepeing only his
name. And we want a book's content to be an array of strings, not an
array of objects with a `content` key. Let's write a JUTE template
which will perform this transformation.

We'll start our template with a `type: book` flag:

```yaml
type: "book"
```

Yes, this tiny document is a valid JUTE template which will always
procude a `{"type": "book"}` result regardless the input
data. Actually, everything in a JUTE is treated as a constant value
unless it doesn't contain a special flag - a dollar sign. A dollar
sign can appear either in a object keys or as the first character of a
string. Numbers and boolean values (`true`/`false`) are always
constants in JUTE templates.

Let's move to the `author` field. Obviously we're gonna use the
incoming data to get an author's name from it:

```yml
type: "book"
author: "$ book.author.name"
```

## License

Copyright © 2019 Health Samurai Team

Distributed under the MIT License.
