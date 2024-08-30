project('jxlatte', 'java', license: 'MIT', version:'0.1.0')

compiler = meson.get_compiler('java')

flags = []

has_release_11 = compiler.compiles('', args: '--release=11', name: '--release=11')
if (has_release_11)
    flags += ['--release=11']
endif

has_xlint = compiler.compiles('', args: '-Xlint', name: '-Xlint')
if (has_xlint)
    flags += ['-Xlint']
endif

add_project_arguments(flags, language: 'java')

subdir('java')
