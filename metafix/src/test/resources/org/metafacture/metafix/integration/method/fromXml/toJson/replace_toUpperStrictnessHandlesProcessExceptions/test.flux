FLUX_DIR + "input.xml"
|open-file
|decode-xml
|handle-marcxml
|fix(FLUX_DIR + "test.fix", strictness="expression", strictnessHandlesProcessExceptions="true")
|encode-json(prettyPrinting="true")
|write(FLUX_DIR + "output-metafix.json")
;
