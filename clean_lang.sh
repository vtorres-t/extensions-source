find . -type f -name "build.gradle.kts" -exec sed -i -E '/listOf\(/ { s/"(id|jv|ca|ceb|cs|da|de|et|eo|fr|it|hi|hu|nl|pl|pt|vi|tr|ru|uk|ar|ko|zh|ja|pt-BR|fi|th|ro|ms|sv|no|ga|eu|lt|hr|he|bg|el|fil|la|co|br|vec|lmo|zh-Hans|sq|bn|fa|lv|sk|sl|ta|ur|zh-Hant|zh-tw|tl|my|is|mo|mn|sr)"(,? ?)//g; s/,\s*,/,/g; s/\(\s*,/(/g; s/,\s*\)/\)/g; s/listOf\(\s*\)//g }' {} +

./gradlew spotlessKotlinApply
