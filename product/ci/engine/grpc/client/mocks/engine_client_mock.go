// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Code generated by MockGen. DO NOT EDIT.
// Source: engine_client.go

// Package grpcclient is a generated GoMock package.
package grpcclient

import (
	gomock "github.com/golang/mock/gomock"
	proto "github.com/harness/harness-core/product/ci/engine/proto"
	reflect "reflect"
)

// MockEngineClient is a mock of EngineClient interface.
type MockEngineClient struct {
	ctrl     *gomock.Controller
	recorder *MockEngineClientMockRecorder
}

// MockEngineClientMockRecorder is the mock recorder for MockEngineClient.
type MockEngineClientMockRecorder struct {
	mock *MockEngineClient
}

// NewMockEngineClient creates a new mock instance.
func NewMockEngineClient(ctrl *gomock.Controller) *MockEngineClient {
	mock := &MockEngineClient{ctrl: ctrl}
	mock.recorder = &MockEngineClientMockRecorder{mock}
	return mock
}

// EXPECT returns an object that allows the caller to indicate expected use.
func (m *MockEngineClient) EXPECT() *MockEngineClientMockRecorder {
	return m.recorder
}

// CloseConn mocks base method.
func (m *MockEngineClient) CloseConn() error {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "CloseConn")
	ret0, _ := ret[0].(error)
	return ret0
}

// CloseConn indicates an expected call of CloseConn.
func (mr *MockEngineClientMockRecorder) CloseConn() *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "CloseConn", reflect.TypeOf((*MockEngineClient)(nil).CloseConn))
}

// Client mocks base method.
func (m *MockEngineClient) Client() proto.LiteEngineClient {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "Client")
	ret0, _ := ret[0].(proto.LiteEngineClient)
	return ret0
}

// Client indicates an expected call of Client.
func (mr *MockEngineClientMockRecorder) Client() *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "Client", reflect.TypeOf((*MockEngineClient)(nil).Client))
}
