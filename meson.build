project('jxlatte', 'java', license: 'MIT', version:'0.1.0')

compiler = meson.get_compiler('java')

has_release_8 = compiler.compiles('', args: '--release=8', name: '--release=8')
if (has_release_8)
    add_project_arguments(['--release=8'], language: 'java')
endif

subdir('java')
