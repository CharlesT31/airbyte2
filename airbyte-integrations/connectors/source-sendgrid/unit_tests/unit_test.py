#
# Copyright (c) 2023 Airbyte, Inc., all rights reserved.
#

import json
import os
import unittest
from unittest.mock import MagicMock, Mock, patch

import pandas as pd
import pendulum
import pytest
import requests
from airbyte_cdk.logger import AirbyteLogger
from airbyte_cdk.models import SyncMode
from numpy import nan
from requests import codes
from source_sendgrid.source import SourceSendgrid
from source_sendgrid.streams import Contacts, SendgridStream

FAKE_NOW = pendulum.DateTime(2022, 1, 1, tzinfo=pendulum.timezone("utc"))
FAKE_NOW_ISO_STRING = FAKE_NOW.to_iso8601_string()


def find_stream(stream_name):
    streams = SourceSendgrid().streams(config={"api_key": "wrong.api.key123"})
    # find by name
    for stream in streams:
        if stream.name == stream_name:
            return stream
    raise ValueError(f"Stream {stream_name} not found")


@pytest.fixture(name="sendgrid_stream")
def sendgrid_stream_fixture(mocker) -> SendgridStream:
    # Wipe the internal list of abstract methods to allow instantiating
    # the abstract class without implementing its abstract methods
    mocker.patch("source_sendgrid.streams.SendgridStream.__abstractmethods__", set())
    # Mypy yells at us because we're initializing an abstract class
    return SendgridStream()  # type: ignore


@pytest.fixture()
def mock_pendulum_now(monkeypatch):
    pendulum_mock = unittest.mock.MagicMock(wraps=pendulum.now)
    pendulum_mock.return_value = FAKE_NOW
    monkeypatch.setattr(pendulum, "now", pendulum_mock)


def test_source_wrong_credentials():
    source = SourceSendgrid()
    status, error = source.check_connection(logger=AirbyteLogger(), config={"api_key": "wrong.api.key123"})
    assert not status


def test_streams():
    streams = SourceSendgrid().streams(config={"api_key": "wrong.api.key123", "start_date": FAKE_NOW_ISO_STRING})

    assert len(streams) == 15


# @patch.multiple(SendgridStreamOffsetPagination, __abstractmethods__=set(), data_field="result")
# def test_pagination(mocker):
#     stream = SendgridStreamOffsetPagination()
#     state = {}
#     response = requests.Response()
#     mocker.patch.object(response, "json", return_value={"result": range(100)})
#     mocker.patch.object(response, "request", return_value=MagicMock())
#     next_page_token = stream.next_page_token(response)
#     request_params = stream.request_params(stream_state=state, next_page_token=next_page_token)
#     assert request_params == {"limit": 50, "offset": 50}


# @patch.multiple(SendgridStreamIncrementalMixin, __abstractmethods__=set())
# def test_stream_state():
#     stream = SendgridStreamIncrementalMixin(start_date=FAKE_NOW_ISO_STRING)
#     state = {}
#     request_params = stream.request_params(stream_state=state)
#     assert request_params == {"end_time": pendulum.now().int_timestamp, "start_date": int(FAKE_NOW.timestamp())}


@pytest.mark.parametrize(
    "stream_name, url , expected",
    (
        ["templates", "https://api.sendgrid.com/v3/templates", []],
        ["lists", "https://api.sendgrid.com/v3/marketing/lists", []],
        ["campaigns", "https://api.sendgrid.com/v3/marketing/campaigns", []],
        ["segments", "https://api.sendgrid.com/v3/marketing/segments/2.0", []],
        ["blocks", "https://api.sendgrid.com/v3/suppression/blocks", ["name", "id", "contact_count", "_metadata"]],
        ["suppression_group_members", "https://api.sendgrid.com/v3/asm/suppressions", ["name", "id", "contact_count", "_metadata"]],
        ["suppression_groups", "https://api.sendgrid.com/v3/asm/groups", ["name", "id", "contact_count", "_metadata"]],
        ["global_suppressions", "https://api.sendgrid.com/v3/suppression/unsubscribes", ["name", "id", "contact_count", "_metadata"]],
    ),
)
def test_read_records(
    stream_name,
    url,
    expected,
    requests_mock,
):
    stream = find_stream(stream_name)
    requests_mock.get("https://api.sendgrid.com/v3/marketing", json={})
    requests_mock.get(url, json={"name": "test", "id": "id", "contact_count": 20, "_metadata": {"self": "self"}})
    records = list(stream.read_records(sync_mode=SyncMode))
    if len(records) == 0:
        assert [] == expected
    else:
        keys = list(records[0].keys())
        assert keys == expected


@pytest.mark.parametrize(
    "stream_name, expected",
    (
        ["templates", "templates"],
        ["lists", "marketing/lists"],
        ["campaigns", "marketing/campaigns"],
        ["contacts", "marketing/contacts/exports"],
        ["segments", "marketing/segments/2.0"],
        ["blocks", "suppression/blocks"],
        ["suppression_group_members", "asm/suppressions"],
        ["suppression_groups", "asm/groups"],
        ["global_suppressions", "suppression/unsubscribes"],
        ["bounces", "suppression/bounces"],
        ["invalid_emails", "suppression/invalid_emails"],
        ["spam_reports", "suppression/spam_reports"],
    ),
)
def test_path(stream_name, expected):
    stream = find_stream(stream_name)
    if hasattr(stream, "path"):
        path = stream.path()  # Contacts for example
    else:
        path = stream.retriever.requester.get_path(stream_state=None, stream_slice=None, next_page_token=None)

    assert path == expected


@pytest.mark.parametrize(
    "stream_name, status, expected",
    (
        ("blocks", 400, False),
        ("blocks", 429, True),
        ("suppression_group_members", 401, False),
    ),
)
def test_should_retry_on_permission_error(stream_name, status, expected):
    stream = find_stream(stream_name)
    response_mock = MagicMock()
    response_mock.status_code = status
    assert stream.retriever.requester._should_retry(response_mock) == expected


def test_compressed_contact_response(requests_mock):
    stream = Contacts()
    with open(os.path.dirname(__file__) + "/compressed_response", "rb") as file_response:
        url = "https://api.sendgrid.com/v3/marketing/contacts/exports"
        requests_mock.register_uri("POST", url, [{"json": {"id": "random_id"}, "status_code": 202}])
        url = "https://api.sendgrid.com/v3/marketing/contacts/exports/random_id"
        resp_bodies = [
            {"json": {"status": "pending", "id": "random_id", "urls": []}, "status_code": 202},
            {"json": {"status": "ready", "urls": ["https://sample_url/sample_csv.csv.gzip"]}, "status_code": 202},
        ]
        requests_mock.register_uri("GET", url, resp_bodies)
        requests_mock.register_uri("GET", "https://sample_url/sample_csv.csv.gzip", [{"body": file_response, "status_code": 202}])
        recs = list(stream.read_records(sync_mode=SyncMode.full_refresh))
        decompressed_response = pd.read_csv(os.path.dirname(__file__) + "/decompressed_response.csv", dtype=str)
        expected_records = [
            {k.lower(): v for k, v in x.items()} for x in decompressed_response.replace({nan: None}).to_dict(orient="records")
        ]

        assert recs == expected_records


def test_uncompressed_contact_response(requests_mock):
    stream = Contacts()
    with open(os.path.dirname(__file__) + "/decompressed_response.csv", "rb") as file_response:
        url = "https://api.sendgrid.com/v3/marketing/contacts/exports"
        requests_mock.register_uri("POST", url, [{"json": {"id": "random_id"}, "status_code": 202}])
        url = "https://api.sendgrid.com/v3/marketing/contacts/exports/random_id"
        resp_bodies = [
            {"json": {"status": "pending", "id": "random_id", "urls": []}, "status_code": 202},
            {"json": {"status": "ready", "urls": ["https://sample_url/sample_csv.csv.gzip"]}, "status_code": 202},
        ]
        requests_mock.register_uri("GET", url, resp_bodies)
        requests_mock.register_uri("GET", "https://sample_url/sample_csv.csv.gzip", [{"body": file_response, "status_code": 202}])
        recs = list(stream.read_records(sync_mode=SyncMode.full_refresh))
        decompressed_response = pd.read_csv(os.path.dirname(__file__) + "/decompressed_response.csv", dtype=str)
        expected_records = [
            {k.lower(): v for k, v in x.items()} for x in decompressed_response.replace({nan: None}).to_dict(orient="records")
        ]

        assert recs == expected_records


def test_bad_job_response(requests_mock):
    stream = Contacts()
    url = "https://api.sendgrid.com/v3/marketing/contacts/exports"

    requests_mock.register_uri(
        "POST", url, [{"json": {"errors": [{"field": "field_name", "message": "error message"}]}, "status_code": codes.BAD_REQUEST}]
    )
    with pytest.raises(Exception):
        list(stream.read_records(sync_mode=SyncMode.full_refresh))


def test_read_chunks_pd():
    stream = Contacts()
    with open("file_not_exist.csv", "w"):
        pass
    list(stream.read_with_chunks(path="file_not_exist.csv", file_encoding="utf-8"))
    with pytest.raises(FileNotFoundError):
        list(stream.read_with_chunks(path="file_not_exist.csv", file_encoding="utf-8"))
