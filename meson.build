project('jxlatte', 'java', license: 'MIT', version:'0.1.0')

compiler = meson.get_compiler('java')

has_release_17 = compiler.compiles('', args: '--release=17', name: '--release=17')
if (has_release_17)
    add_project_arguments(['--release=17'], language: 'java')
endif

subdir('java')
