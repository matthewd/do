# encoding: utf-8

require File.expand_path(File.join(File.dirname(__FILE__), 'spec_helper'))
require 'data_objects/spec/result_spec'

# splitting the descibe into two separate declaration avoids
# concurrent execution of the "it_should_behave_like ....."
# needed by some databases (sqlite3)

describe DataObjects::Hsqldb::Result do
  it_should_behave_like 'a Result'
end

describe DataObjects::Hsqldb::Result do
  it_should_behave_like 'a Result which returns inserted keys'
end
