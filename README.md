# jute.clj

A Clojure/ClojureScript (Java/JavaScript) implementation of [JUTE data
mapping language](https://github.com/HealthSamurai/jute.js). Still in
early development stage.

[![Build Status](https://travis-ci.org/HealthSamurai/jute.clj.svg?branch=master)](https://travis-ci.org/HealthSamurai/jute.clj)

[![Clojars Project](https://img.shields.io/clojars/v/com.health-samurai/jute.svg)](https://clojars.org/com.health-samurai/jute)

[**Online Demo**](https://storage.googleapis.com/jute-demo-site/index.html)

# Introduction

JUTE stands for **J**SON **U**niform **Te**mplates and it's a small language to
describe JSON documents transformations. JUTE templates are JSON
documents itself. It's safe to evaluate user-provided JUTE templates,
there is no way for a template to currupt a runtime environment [if
you use a safe YAML
parser](https://arp242.net/yaml-config.html#insecure-by-default).

# Few words about YAML

JSON format wasn't designed for ease of use by human beings, it's
relatively hard to write JSON by hands. That's why JUTE's primary
format is [YAML](https://yaml.org/), which is much easier to read and
write, thanks to its clean syntax and indentation-based nesting. Don't
be confused with it, YAML and JSON are interchangeable, and there are
even [online conversion tools](https://www.json2yaml.com/) beetween them:

<table>
<thead>
<tr><th>JSON</th><th>YAML</th></tr>
</thead>
<tbody>
<tr><td>

```yml
{
  "speaker": {
    "login": "mlapshin",
    "email": "mlapshin@health-samurai.io"
  },
  "fhir?": true,
  "topics": [
    "mapping",
    "dsl",
    "jute",
    "fhir"
  ]
}
```
</td><td>

```yml
speaker:
  login: mlapshin
  email: mlapshin@health-samurai.io
fhir?: true
topics:
  - mapping
  - dsl
  - jute
  - fhir
```
</td></tr>
</tbody>
</table>

# Quickstart Tutorial

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

Here we're going to discard preface and
afterwords as well as minor author information keepeing only his
name. And we want a book's content to be an array of strings, not an
array of objects with a `content` key. Let's write a JUTE template
which will perform this transformation.

We'll start our template with a `type: book` flag:

<table>
<thead>
<tr><th>Template</th><th>Result</th></tr>
</thead>
<tbody>
<tr><td>

```yml
type: "book"
```
</td><td>

```yml
type: "book"
```
</td></tr>
</tbody>
</table>

This tiny document is a valid JUTE template which will always procude
a `{"type": "book"}` result regardless the input data. Actually,
everything in a JUTE is treated as a constant value unless it doesn't
contain a special flag - a dollar sign. A dollar sign can appear
either in a object keys or as the first character of a string. Numbers
and boolean values (`true`/`false`) are always constants in JUTE
templates.

Let's move to the `author` field. Obviously we're gonna take an
author's name from an incoming data:

<table>
<thead>
<tr><th>Template</th><th>Result</th></tr>
</thead>
<tbody>
<tr><td>

```yml
type: "book"
author: "$ book.author.name"
```
</td><td>

```yml
type: "book"
author: "M. Soloviev"
```
</td></tr>
</tbody>
</table>

To tell JUTE that an `author` field will be dynamic we put a dollar
sign at the beginning of a value's string. The rest of the string is a
path for the data we need. Such strings starting with a dollar signs
are called JUTE **expressions** and they have pretty rich syntax to
describe various operations on an incoming data or a **scope**.

One of expression's abilities is an extract data by **path**. Every
path consists of one or several **path components** separated by
dot. In simpliest case a path component is a field name where JUTE
interpreter will dig to get value. In our case it fill take the `book`
field from the scope root, then `author`, then `name`. You can use
digits as path component as well to get N-th value from an
array. Array indices are starting with 0.

Please note that it's ok to omit double-quotes (`"`) for strings in
YAML, so instead of writing `"$ foo.bar"` we can just write `$
foo.bar`.

We can fill the `title` field using similar path expression and omit
double-quotes for readability:

<table>
<thead>
<tr><th>Template</th><th>Result</th></tr>
</thead>
<tbody>
<tr><td>

```yml
type: book
author: $ book.author.name
title: $ book.title
```
</td><td>

```yml
type: book
author: M. Soloviev
title: Approach to Cockroach
```
</td></tr>
</tbody>
</table>

Let's proceed to the `content` part. We need to filter out chapters
where `type` doesn't equal to `"content"`. There is a special type of
a path element to do this called **predicate search**:

<table>
<thead>
<tr><th>Template</th><th>Result</th></tr>
</thead>
<tbody>
<tr><td>

```yml
type: book
author: $ book.author.name
title: $ book.title
content: $ book.chapters.*(this.type = "content")
```
</td><td>

```yml
type: book
author: M. Soloviev
title: Approach to Cockroach
content:
- type: content
  content: Chapter 1
- type: content
  content: Chapter 2
- type: content
  content: Chapter 3
```
</td></tr>
</tbody>
</table>

Instead of telling an exact path, we describe a condition which an
array element should met to be selected for the next step of path
evaluation. Use `this` keyword to reference current element in an
array. A result of a predicate search is always an array, even if
there is only one element matching criteria.

The final step is to extract `content` property from every element in
the `content` array. In most programming languages nowadays it's done
using a [map
function](https://en.wikipedia.org/wiki/Map_(higher-order_function))
which executes same code on every element in an array and returns
results an array with preserved order. In JUTE we have `map` as well,
but it's not a function, it's a **directive**:

<table>
<thead>
<tr><th>Template</th><th>Result</th></tr>
</thead>
<tbody>
<tr><td>

```yml
type: book
author: $ book.author.name
title: $ book.title
content: 
  $map: $ book.chapters.*(this.type = "content")
  $as: i
  $body: $ i.content
```
</td><td>

```yml
type: book
author: M. Soloviev
title: Approach to Cockroach
content:
- Chapter 1
- Chapter 2
- Chapter 3
```
</td></tr>
</tbody>
</table>

A directive is an object in a template with one or several keys
starting with a dollar sign. A dollar sign tells JUTE that this object
needs to be evaluated in a special way depending on directive's
purpose. In case of `$map` it takes a value from a `$map` key,
iterates through it and executes `$body` on every element aliasing it
with a name from `$as` key. Other available directives are `$if`,
`$switch`, `$fn` and `$call` - you'll find all of them in the reference.

That's it, in this tutorial we wrote a simple template and touched a
little bit every aspect of a JUTE language.

# An FizzBuzz Example

A classicall [FizzBuzz](http://wiki.c2.com/?FizzBuzzTest) programm in JUTE:

```yaml
$call: join-str
$args:
  - " "
  - $map: $ range(0, 50, 1)
    $as: num
    $body:
      $let:
        - s: ""

        - s:
            $if: $ num % 3 = 0
            $then: $ s + "Fizz"
            $else: $ s

        - s:
            $if: $ num % 5 = 0
            $then: $ s + "Buzz"
            $else: $ s
      $body:
        $if: $ s = ""
        $then: $ num
        $else: $ toString(num) + "-" + s
```

# Reference

## Terminology

**Template** - a JSON-like data structure to be evaluated by JUTE.

**Scope** - an object where JUTE looks up values and functions to
evaluate expressions and directives. 

**Expression** - a string value within a template starting with a
dollar sign which will be evaluated by JUTE.

**Directive** - an object within a template containing one or several
keys starting with a dollar sign with custom evaluation logic.

## Expressions

I'm quite short in time right now to describe full expressions
syntax. To get some understaing of them please take a look at the
[expressions test
suite](https://github.com/HealthSamurai/jute.clj/blob/master/spec/expressions.yml). Commented
out pieces are still need to be implemented.

Available operators are: `!= = ! * % <= / - >= < + > && ||`

## Directives

### $if

Performs conditional evaluation:

```yaml
gender:
  $if: $ sex = "m"
  $then: Male
  $else: Female
```

If condition is true, direcitve is evaluated into a value of `$then`,
`$else` otherwise. If a condition is false and `$else` is omitted,
directive evaluates into null.

NB there is a short form of `$if` directive:

```yaml
patientName:
  $if: patient.firstName && patient.lastName
  firstName: $ patient.firstName
  lastName: $ patient.lastName
```

In a shortened form directrive is evaluated into itself (without the
`$if` attribute) when condition is true, null otherwise.

### $map

`$map` directive evaluates into array containing results of applying
it's `$body` on every element from a `$map` array. Array element is
aliased by name from `$as` field. If `$as` is ommited, `this` is used
instead.

```yaml
funnyStuff:
  $map: 
  - 1
  - 2
  - 3
  - 4
  $as: item
  $body: $ item * 2
```

Alternatively `$as` field can be an array of two elements, the first
is a name of a variable for the current item and the second is a name
for a variable containing item index:

```yaml
funnyStuff:
  $map: $ people
  $as: [guy, idx]
  $body: $ "hello, " + idx
```


### $reduce

To be done later.

### $let

`$let` directive evaluates into it's `$body` with scope extended with
additional values:

```yaml
$let:
  pi: 3.1415
  radius: 3
$body:
  area: $ pi * radius * radius
  perimeter: $ pi * 2 * radius
```

### $fn

`$fn` directive returns a function which can be invoked later in an
expresson. Value of an `$fn` key is an array containing names of
function arguments. `$body` key contains function body.

Most likely you'll put an `$fn` directive into `$let` directive to
make function accessible inside `$let`'s body:

```yaml
$let:
  circleArea:
    $fn: ["radius"]
    $body: $ 3.1415 * radius * radius
$body:
  area: $ circleArea(circles.0.radius)
```

### $call

`$call` directive is a way to call a function outside of JUTE
expression:

```yaml
fullName:
  $call: joinStr
  $args:
    - " "
    - - $ pt.firstName
      - $ pt.middleName
      - $ pt.lastName
```

### $switch

`$switch` directive takes a value of an expression and then compares
it to all directive-level keys. If matching key found, directive
evaluates into a value of corresponding key. `$default` key (if
present) is used when no matching key was found. Evaluates to null if
no match was found and there is no `$default` key.

```yaml
gender:
  $switch: $ patient.sex
  M: male
  F: female
  U: unknown
  $default: other
```

### $reduce

`$reduce` directive performs standard [reduce
operation](https://en.wikipedia.org/wiki/Fold_(higher-order_function)):

```yaml
sum:
  $reduce: $ range(0, 10, 1)
  $as: ["acc", "i"]
  $start: 0
  $body: $ acc + i
```

## Functions

To be written.

### joinStr(separator, strArray)

### splitStr(srt, regexp, limit?)

### substr(str, start, end)

### concat(arrays...)

### merge(objects...)

### str(val)

### toInt(str)

### toDec(str)

### hash(any)

### groupBy(fn, array)

### len(any)

### toLowerCase(s)

### toUpperCase(s)

### range(begin, end, step)

### flatten(array)

### assoc(object, key, value, ...)

### abs(signed int)

### dropBlanks(any)

### uniq(array)

# License

Copyright © 2019 Health Samurai Team

Distributed under the MIT License.
