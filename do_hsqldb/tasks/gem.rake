require 'rubygems/package_task'

GEM_SPEC = eval(File.read('do_hsqldb.gemspec'))

gem_package = Gem::PackageTask.new(GEM_SPEC) do |pkg|
  pkg.need_tar = false
  pkg.need_zip = false
end
