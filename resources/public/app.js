const codeMirror = CodeMirror;

// require("codemirror/mode/javascript/javascript.js");
// require("codemirror/addon/lint/yaml-lint.js");
// require("codemirror/lib/codemirror.css");


const yaml = YAML;
const debounce = (func, delay) => {
  let inDebounce;
  return function(){
    const context = this;
    const args = arguments;
    clearTimeout(inDebounce);
    inDebounce = setTimeout(() => func.apply(context, args), delay);
  };
};

var pathNode = document.getElementById("path");
var outNode = document.getElementById("output");

function evaluate(){
  try  {
    var inputType = document.querySelector('input[name="inputType"]:checked').value
    var inputText = cm.getValue();
    var res = inputType === 'yaml' ? yaml.load(inputText) : JSON.parse(inputText);
    console.log(pathNode.value, res);
    var result = fhirpath.evaluate(res, pathNode.value);
    outNode.innerHTML = '<pre />';
    outNode.childNodes.item(0).textContent = yaml.dump(result);
  } catch (e) {
    outNode.innerHTML = '<pre style="color: red;" />';
    outNode.childNodes.item(0).textContent = e.toString();
    console.error(e);
  }
}


var example = {
  a: 1 
};

var jute = codeMirror(document.getElementById("jute"), {
  value: yaml.dump(example),
  lineNumbers: true,
  mode:  "yaml"
});

var baseUrl = "/";
var juteChanged = (x)=>{
  fetch(baseUrl, {
    method: 'POST',
    body: JSON.stringify({
      jute: jute.getValue(),
      source: source.getValue() 
    })
  }).then((resp)=>{
    return resp.text();
  }).then((res)=>{
    target.setValue(res);
  }) ;
};

jute.on('change', debounce(juteChanged, 300));

var pt = {
  resourceType: "Patient",
  name: [
    {given: ["Nikolai"], family: "Ryzhikov"}
  ]
};

var source = codeMirror(document.getElementById("source"), {
  value: yaml.dump(pt),
  lineNumbers: true,
  mode:  "yaml"
});

source.on('change', debounce(juteChanged, 300));

juteChanged();

var target = codeMirror(document.getElementById("result"), {
  lineNumbers: true,
  mode:  "yaml"
});
