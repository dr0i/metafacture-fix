FLUX_DIR + "input.json"
|open-file
|as-records
|decode-json
|fix(FLUX_DIR + "test.fix", strictness="expression")
|encode-json
|write(FLUX_DIR + "output-metafix.json")
;
