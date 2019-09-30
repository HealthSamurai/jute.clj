const jute = require("../js/jute.js").jute.js;
const tap = require("tap");
const yaml = require("js-yaml");
const fs = require('fs');

const specFiles = [
  "fn_directive.yml",
  "if_directive.yml",
  "call_directive.yml",
  "let_directive.yml",
  "special_options.yml",
  "switch_directive.yml",
  "map_directive.yml",
  "reduce_directive.yml",
  "functions_spec.yml",
  "expressions.yml"
];

specFiles.forEach((f) => {
  const spec = yaml.safeLoad(fs.readFileSync(`../spec/${f}`, {encoding: "utf-8"}));

  tap.test(spec.suite, t => {
    spec.tests.forEach(specTest => {
      t.test(specTest.desc, t => {
        const tpl = jute.compile(specTest.template, specTest.options || {});
        const result = tpl(specTest.scope);
        
        t.strictSame(result, specTest.result, specTest.desc);
        t.done();
      });
    })

    t.done();
  });
});


tap.test("Exceptions", t => {
  t.test("reports template path where compilation failed", t => {
    t.throws(() => {
      jute.compile({foo: ["$ 30c"]}, {});
    }, {
      message: "While compiling JUTE template at @.foo.0",
      path: ["@", "foo", 0]
    });

    t.done();
  });

  t.test("reports template path where evaluation failed", t => {
    t.throws(() => {
      const t = jute.compile({foo: ["$ foo(a)"]}, {});
      t({a: 0});
    }, {
      message: "While evaluating JUTE template at @.foo.0",
      path: ["@", "foo", 0],
      pathStr: "@.foo.0"
    });

    t.done();
  });


  t.done();
});
