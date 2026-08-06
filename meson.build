project('jxlatte', 'java', license: 'MIT', version:'0.1.0')

compiler = meson.get_compiler('java')

flags = ['--release=21']

has_release_21 = compiler.compiles('', args: '--release=21', name: '--release=21')
if (not has_release_21)
    error('Must support Java 21')
endif

has_xlint = compiler.compiles('', args: '-Xlint', name: '-Xlint')
if (has_xlint)
    flags += ['-Xlint']
endif

add_project_arguments(flags, language: 'java')

subdir('java')
