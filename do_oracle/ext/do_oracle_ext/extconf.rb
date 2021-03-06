if RUBY_PLATFORM =~ /darwin/
  ENV["RC_ARCHS"] = `uname -m`.chomp if `uname -sr` =~ /^Darwin/

  # On PowerPC the defaults are fine
  ENV["RC_ARCHS"] = '' if `uname -m` =~ /^Power Macintosh/
end

# Loads mkmf which is used to make makefiles for Ruby extensions
require 'mkmf'

# need to check dynamically for libraries and include files directories
def config_value(type)
  case type
  when 'libdir'
    '/usr/local/oracle/instantclient_10_2'
  when 'includedir'
    '/usr/local/oracle/instantclient_10_2/sdk/include'
  end
end

def have_build_env
  # have_library('occi') &&
  # have_library('clntsh') &&
  # have_header('oci.h')
  true
end

# dir_config('oracle-client', config_value('includedir'), config_value('libdir'))

if have_build_env

  $CFLAGS << ' -Wall ' unless RUBY_PLATFORM =~ /mswin/
  if RUBY_VERSION < '1.8.6'
    $CFLAGS << ' -DRUBY_LESS_THAN_186'
  end

  create_makefile("do_oracle_ext")
else
  puts 'Could not find Oracle build environment (libraries & headers): Makefile not created'
  exit(1)
end
