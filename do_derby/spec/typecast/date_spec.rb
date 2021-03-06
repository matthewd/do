# encoding: utf-8

require File.expand_path(File.join(File.dirname(__FILE__), '..', 'spec_helper'))
require 'data_objects/spec/typecast/date_spec'

describe 'DataObjects::Derby with Date' do
  it_should_behave_like 'supporting Date'
  it_should_behave_like 'supporting Date autocasting'
end
