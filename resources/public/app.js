const codeMirror = CodeMirror;

// require("codemirror/mode/javascript/javascript.js");
// require("codemirror/addon/lint/yaml-lint.js");
// require("codemirror/lib/codemirror.css");

const yaml = YAML;
const debounce = (func, delay) => {
  let inDebounce;
  return function() {
    const context = this;
    const args = arguments;
    clearTimeout(inDebounce);
    inDebounce = setTimeout(() => func.apply(context, args), delay);
  };
};

var pathNode = document.getElementById("path");
var outNode = document.getElementById("output");

function evaluate() {
  try {
    var inputType = document.querySelector('input[name="inputType"]:checked')
      .value;
    var inputText = cm.getValue();
    var res =
      inputType === "yaml" ? yaml.load(inputText) : JSON.parse(inputText);
    console.log(pathNode.value, res);
    var result = fhirpath.evaluate(res, pathNode.value);
    outNode.innerHTML = "<pre />";
    outNode.childNodes.item(0).textContent = yaml.dump(result);
  } catch (e) {
    outNode.innerHTML = '<pre style="color: red;" />';
    outNode.childNodes.item(0).textContent = e.toString();
    console.error(e);
  }
}

var example = {
  $let: {
    prefix: "Mr",
    greetFn: {
      $fn: ["subj", "greeting"],
      $body: '$ greeting + prefix + " " + subj'
    }
  },
  $body: {
    fist_name: "$fp name.given.first()",
    last_name: "$fp name.family.first()",
    greeting_a: '$ greetFn("world", "hello, ")',
    greeting_b: '$ greetFn("sandman", "bring me a dream, ")',
    map: {
      $map: "$ name",
      $as: "x",
      $body: {
        name: "$fp %prefix & ' ' &  %x.given.first() & ' ' & %x.family.first()"
      }
    },
    sex: {
      $if: "$fp gender = 'Male'",
      $then: "M",
      $else: "F"
    },
    letandif: {
      $let: { x: 2 },
      $body: {
        $if: "$ a = 1",
        $then: 1,
        $else: "$ b"
      }
    }
  }
};

var jute = codeMirror(document.getElementById("jute"), {
  value: yaml.stringify(example, 100, 2),
  lineNumbers: true,
  mode: "yaml"
});

var baseUrl = "/";
var juteChanged = x => {
  fetch(baseUrl, {
    method: "POST",
    body: JSON.stringify({
      jute: jute.getValue(),
      source: source.getValue()
    })
  })
    .then(resp => {
      return resp.text();
    })
    .then(res => {
      target.setValue(res);
    });
};

jute.on("change", debounce(juteChanged, 300));

var pt = {
  resourceType: "Patient",
  a: 2,
  b: "Hoho",
  gender: "Male",
  name: [
    { use: "official", given: ["Nikolai"], family: "Ryzhikov" },
    { user: "alias", given: ["Nik"], family: "Got" }
  ]
};

var source = codeMirror(document.getElementById("source"), {
  value: yaml.stringify(pt, null, 2),
  lineNumbers: true,
  mode: "yaml"
});

source.on("change", debounce(juteChanged, 300));

juteChanged();

var target = codeMirror(document.getElementById("result"), {
  lineNumbers: true,
  mode: "yaml"
});
