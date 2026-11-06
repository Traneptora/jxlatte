project('jxlatte', 'java', license: 'MIT', version:'0.1.0')

compiler = meson.get_compiler('java')

flags = ['--release=8']

has_release_8 = compiler.compiles('', args: '--release=8', name: '--release=8')
if (not has_release_8)
    error('Must support Java 8 as a target')
endif

has_xlint = compiler.compiles('', args: '-Xlint', name: '-Xlint')
if (has_xlint)
    flags += ['-Xlint', '-Xlint:-options']
endif

add_project_arguments(flags, language: 'java')

subdir('src')
