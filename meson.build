project('jxlatte', 'java', license: 'MIT', version:'0.1.0')

compiler = meson.get_compiler('java')

has_release_11 = compiler.compiles('', args: '--release=11', name: '--release=11')
if (has_release_11)
    add_project_arguments(['--release=11'], language: 'java')
endif

subdir('java')
