project('jxlatte', 'java', license: 'MIT', version:'0.1.0')

compiler = meson.get_compiler('java')

has_release_21 = compiler.compiles('', args: '--release=21', name: '--release=21')
if (has_release_21)
    add_project_arguments(['--release=21'], language: 'java')
endif

subdir('java')
