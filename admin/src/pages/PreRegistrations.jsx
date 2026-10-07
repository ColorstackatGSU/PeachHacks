import { useCallback, useId, useMemo, useState } from "react";
import { api } from "../api/client.js";
import { EmptyBlock, ErrorBlock, LoadingBlock, PageHeader, Pagination, Tag, useToast } from "../components/ui.jsx";
import { errorText, formatDateTime, fullName } from "../lib/format.js";
import { schoolOptions, useAsync, useDebounced, useStats } from "../lib/hooks.js";

const PAGE_SIZE = 25;

export default function PreRegistrations() {
  const notify = useToast();
  const ids = useId();
  const [search, setSearch] = useState("");
  const [school, setSchool] = useState("");
  const [schoolEmailConfirmed, setSchoolEmailConfirmed] = useState("");
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);
  const [resending, setResending] = useState(null);

  const q = useDebounced(search.trim(), 300);
  const filters = useMemo(() => ({ q, school, schoolEmailConfirmed }), [q, school, schoolEmailConfirmed]);
  const load = useCallback((signal) => api.preRegistrations({ page, size: PAGE_SIZE, ...filters }, signal), [page, filters]);
  const list = useAsync(load);
  const stats = useStats();
  const schools = schoolOptions(stats.data, "preRegistrations");

  const items = list.data?.items || [];
  const total = list.data?.total || 0;
  const filtered = Boolean(q || school || schoolEmailConfirmed);

  const exportCsv = async () => {
    setExporting(true);
    try {
      await api.exportPreRegistrations(filters);
    } catch (error) {
      notify(`Export failed: ${errorText(error)}`, "error");
    } finally {
      setExporting(false);
    }
  };

  const resendSchoolEmail = async (item) => {
    setResending(item.id);
    try {
      await api.resendPreRegistrationSchoolEmail(item.id);
      notify(`Confirmation link sent to ${item.schoolEmail}.`);
    } catch (error) {
      notify(`Could not send the link: ${errorText(error)}`, "error");
    } finally {
      setResending(null);
    }
  };

  return (
    <>
      <PageHeader title="Pre-registrations" description="People who asked to be told when registration opens.">
        <button type="button" className="btn" onClick={exportCsv} disabled={exporting}>
          {exporting ? "Preparing…" : filtered ? "Export filtered CSV" : "Export CSV"}
        </button>
      </PageHeader>

      <form className="filters" role="search" onSubmit={(e) => e.preventDefault()}>
        <div className="field field-grow">
          <label htmlFor={`${ids}-q`}>Search</label>
          <input
            id={`${ids}-q`}
            type="search"
            placeholder="Name or email"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
          />
        </div>
        <div className="field">
          <label htmlFor={`${ids}-school`}>School</label>
          <select
            id={`${ids}-school`}
            value={school}
            onChange={(e) => {
              setSchool(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All schools</option>
            {schools.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor={`${ids}-school-email`}>School email</label>
          <select
            id={`${ids}-school-email`}
            value={schoolEmailConfirmed}
            onChange={(e) => {
              setSchoolEmailConfirmed(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Everyone</option>
            <option value="true">Confirmed</option>
            <option value="false">Unconfirmed</option>
          </select>
        </div>
        {filtered && (
          <button
            type="button"
            className="btn"
            onClick={() => {
              setSearch("");
              setSchool("");
              setSchoolEmailConfirmed("");
              setPage(0);
            }}
          >
            Clear
          </button>
        )}
      </form>

      {list.error && <ErrorBlock error={list.error} onRetry={list.reload} />}
      {!list.error && !list.data && <LoadingBlock label="Loading pre-registrations…" />}
      {!list.error && list.data && items.length === 0 && (
        <EmptyBlock title={filtered ? "No pre-registrations match" : "No pre-registrations yet"}>
          {filtered ? "Try a different search or clear the filters." : "They will appear here as people sign up on the public site."}
        </EmptyBlock>
      )}

      {list.data && items.length > 0 && (
        <div className={`table-wrap${list.loading ? " is-loading" : ""}`} aria-busy={list.loading}>
          <table className="data-table">
            <caption className="sr-only">Pre-registrations, newest first</caption>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Email</th>
                <th scope="col">School</th>
                <th scope="col">School email</th>
                <th scope="col">Status</th>
                <th scope="col">Pre-registered</th>
                <th scope="col">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id}>
                  <th scope="row" data-label="Name">
                    {fullName(item)}
                  </th>
                  <td data-label="Email" className="cell-break">
                    {item.email}
                  </td>
                  <td data-label="School">{item.school}</td>
                  <td data-label="School email" className="cell-break">
                    {item.schoolEmail ? (
                      <span className="tag-row">
                        {item.schoolEmail}
                        {item.schoolEmailConfirmed ? <Tag tone="accepted">Confirmed</Tag> : <Tag tone="waitlisted">Unconfirmed</Tag>}
                      </span>
                    ) : (
                      <span className="muted">None</span>
                    )}
                  </td>
                  <td data-label="Status">
                    <span className="tag-row">
                      {item.registered ? <Tag tone="accepted">Registered</Tag> : <Tag tone="neutral">Not registered</Tag>}
                      {item.unsubscribed && <Tag tone="rejected">Unsubscribed</Tag>}
                    </span>
                  </td>
                  <td data-label="Pre-registered" className="cell-nowrap">
                    {formatDateTime(item.createdAt)}
                  </td>
                  <td className="cell-actions">
                    {item.schoolEmail && !item.schoolEmailConfirmed && (
                      <button
                        type="button"
                        className="btn btn-small"
                        aria-label={`Resend the school email confirmation link to ${fullName(item)}`}
                        disabled={resending === item.id}
                        onClick={() => resendSchoolEmail(item)}
                      >
                        {resending === item.id ? "Sending…" : "Resend link"}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {list.data && total > 0 && (
        <Pagination page={page} size={list.data.size || PAGE_SIZE} total={total} onPage={setPage} disabled={list.loading} />
      )}

    </>
  );
}
