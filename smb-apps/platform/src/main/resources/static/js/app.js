// Small progressive enhancements shared by every app. Pages work without JavaScript; this only
// adds confirmations, live refresh, image zoom, table filtering and repeatable form rows.
(function () {
  'use strict';

  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    var headers = {};
    if (token && header) {
      headers[header.content] = token.content;
    }
    return headers;
  }

  // <form data-confirm="Send the invoice to Sharma Roadlines?">
  document.addEventListener('submit', function (event) {
    var form = event.target;
    var message = form.getAttribute('data-confirm');
    if (message && !window.confirm(message)) {
      event.preventDefault();
    }
  }, true);

  // <div data-refresh-url="/inbox/live" data-refresh-every="5000">: re-fetch and replace the content.
  function startRefresh(element) {
    var url = element.getAttribute('data-refresh-url');
    var every = parseInt(element.getAttribute('data-refresh-every') || '5000', 10);
    var last = element.innerHTML;
    setInterval(function () {
      if (document.hidden) {
        return;
      }
      fetch(url, { headers: { 'X-Requested-With': 'fetch' }, credentials: 'same-origin' })
        .then(function (response) { return response.ok ? response.text() : null; })
        .then(function (html) {
          if (html !== null && html !== last) {
            last = html;
            element.innerHTML = html;
            element.dispatchEvent(new CustomEvent('refreshed', { bubbles: true }));
          }
        })
        .catch(function () { /* try again next tick */ });
    }, every);
  }

  // Click a document photo to see it at full size.
  document.addEventListener('click', function (event) {
    var image = event.target.closest('.doc-view img');
    if (image) {
      image.classList.toggle('zoomed');
    }
    var row = event.target.closest('tr[data-href]');
    if (row && !event.target.closest('a, button, input, select, form')) {
      window.location = row.getAttribute('data-href');
    }
  });

  // <input data-filter-table="#parts"> hides table rows that do not contain the typed text.
  function startFilter(input) {
    var table = document.querySelector(input.getAttribute('data-filter-table'));
    if (!table) {
      return;
    }
    input.addEventListener('input', function () {
      var needle = input.value.trim().toLowerCase();
      table.querySelectorAll('tbody tr').forEach(function (row) {
        row.style.display = !needle || row.textContent.toLowerCase().indexOf(needle) >= 0 ? '' : 'none';
      });
    });
  }

  // Repeatable rows: <tbody data-rows="lines"> with a <template data-row-template="lines"> and
  // buttons data-add-row="lines" / data-remove-row. Inputs named lines[__i__].qty are renumbered.
  function renumber(body) {
    Array.prototype.forEach.call(body.children, function (row, index) {
      row.querySelectorAll('[name]').forEach(function (field) {
        field.name = field.name.replace(/\[(\d+|__i__)\]/, '[' + index + ']');
      });
    });
  }

  document.addEventListener('click', function (event) {
    var add = event.target.closest('[data-add-row]');
    if (add) {
      event.preventDefault();
      var key = add.getAttribute('data-add-row');
      var body = document.querySelector('[data-rows="' + key + '"]');
      var template = document.querySelector('template[data-row-template="' + key + '"]');
      if (body && template) {
        body.appendChild(template.content.cloneNode(true));
        renumber(body);
      }
    }
    var remove = event.target.closest('[data-remove-row]');
    if (remove) {
      event.preventDefault();
      var tbody = remove.closest('[data-rows]');
      remove.closest('tr').remove();
      if (tbody) {
        renumber(tbody);
      }
    }
  });

  // Keep the simulator conversation scrolled to the newest message.
  function scrollThread() {
    var thread = document.querySelector('.thread');
    if (thread) {
      thread.scrollTop = thread.scrollHeight;
    }
  }
  document.addEventListener('refreshed', scrollThread);

  // <button data-copy="text"> copies to the clipboard (booking links, UPI ids).
  document.addEventListener('click', function (event) {
    var button = event.target.closest('[data-copy]');
    if (button && navigator.clipboard) {
      navigator.clipboard.writeText(button.getAttribute('data-copy'));
      var label = button.textContent;
      button.textContent = 'Copied';
      setTimeout(function () { button.textContent = label; }, 1500);
    }
  });

  // <select data-submit-on-change> submits its form, for filter bars.
  document.addEventListener('change', function (event) {
    if (event.target.matches('[data-submit-on-change]')) {
      event.target.form.submit();
    }
  });

  window.LT = { csrfHeaders: csrfHeaders };

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-refresh-url]').forEach(startRefresh);
    document.querySelectorAll('[data-filter-table]').forEach(startFilter);
    scrollThread();
  });
})();
