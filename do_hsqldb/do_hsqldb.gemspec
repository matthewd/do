require 'lib/do_hsqldb/version'

Gem::Specification.new do |s|
  # basic information
  s.name        = "do_hsqldb"
  s.version     = DataObjects::Hsqldb::VERSION

  # description and details
  s.summary     = 'DataObjects Hsqldb Driver'
  s.description = "Implements the DataObjects API for Hsqldb"

  # dependencies
  s.add_dependency "addressable", "~>2.0"
  s.add_dependency "extlib", "~>0.9.12"
  s.add_dependency "data_objects", DataObjects::Hsqldb::VERSION
  s.add_dependency "jdbc-hsqldb", "~>1.8.0"
  s.add_dependency "do_jdbc", DataObjects::Hsqldb::VERSION

  s.platform = "java"

  # components, files and paths
  s.files = FileList["lib/**/*.rb", "spec/**/*.rb", "tasks/**/*.rake",
                      "LICENSE", "Rakefile", "*.{markdown,rdoc,txt,yml}", "lib/*.jar"]

  # development dependencies
  s.add_development_dependency 'rspec', '~>1.2.0'

  s.require_path = 'lib'

  # documentation
  s.has_rdoc = false

  # project information
  s.homepage          = 'http://github.com/datamapper/do'
  s.rubyforge_project = 'dorb'

  # author and contributors
  s.author      = 'Alex Coles'
  s.email       = 'alex@alexcolesportfolio.com'
end
