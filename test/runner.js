const jute = require("../js/jute.js");

const t = jute.jute.js.compile({foo: "$ 2 + 2"});

console.log(t({}));
