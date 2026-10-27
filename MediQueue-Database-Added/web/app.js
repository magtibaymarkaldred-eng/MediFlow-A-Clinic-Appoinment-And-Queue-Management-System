const STORE = { session: 'mediqueue_web_session_v1' };
let usersCache = [];
let appointmentsCache = [];
const doctors = [
  { name: 'Dr. Maria Santos', specialty: 'General Medicine', hours: '9:00 AM – 3:00 PM' },
  { name: 'Dr. Pedro Reyes', specialty: 'Pediatrics', hours: '9:00 AM – 4:00 PM' },
  { name: 'Dr. Juan Cruz', specialty: 'Dermatology', hours: '10:00 AM – 2:00 PM' },
  { name: 'Dr. Ana Aquino', specialty: 'Internal Medicine', hours: '9:00 AM – 12:00 PM' }
];
let currentUser = null;
let activePage = 'overview';
let toastTimer;
const $ = id => document.getElementById(id);
const getUsers = () => usersCache;
const getAppointments = () => appointmentsCache;
function saveAppointments(data) {
  appointmentsCache = data;
  fetch('/api/appointments', {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)})
    .then(async response => { if (!response.ok) throw new Error('Save failed'); })
    .catch(() => showToast('Could not save to SQLite. Check that the Java server is running.'));
}
const today = () => new Date().toISOString().slice(0, 10);
const prettyDate = value => value ? new Date(value + 'T00:00:00').toLocaleDateString(undefined, {month:'short', day:'numeric', year:'numeric'}) : '—';
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const initials = name => (name || '?').split(/\s+/).slice(0,2).map(x => x[0]).join('').toUpperCase();
const isAdmin = () => currentUser && currentUser.role === 'admin';
const statusClass = status => status.toLowerCase().replace(/\s+/g, '-');
const badge = status => `<span class="badge ${statusClass(status)}">${esc(status)}</span>`;
function showToast(message) {
  $('toast').textContent = message; $('toast').classList.add('show');
  clearTimeout(toastTimer); toastTimer = setTimeout(() => $('toast').classList.remove('show'), 2600);
}
async function init() {
  $('year').textContent = new Date().getFullYear();
  $('loginForm').addEventListener('submit', handleLogin);
  $('logoutBtn').addEventListener('click', logout);
  try {
    const response = await fetch('/api/data');
    if (!response.ok) throw new Error('Server unavailable');
    const data = await response.json();
    usersCache = data.users || [];
    appointmentsCache = data.appointments || [];
  } catch (error) {
    $('loginError').textContent = 'Cannot connect to the Java server. Run run.bat and refresh this page.';
    return;
  }
  const session = JSON.parse(sessionStorage.getItem(STORE.session) || 'null');
  if (session) {
    const user = getUsers().find(u => u.username === session.username && u.role === session.role);
    if (user) { currentUser = user; openApp(); }
  }
}
async function handleLogin(event) {
  event.preventDefault();
  const username = $('loginUsername').value.trim().toLowerCase();
  const password = $('loginPassword').value;
  try {
    const response = await fetch('/api/login', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({username,password})});
    const data = await response.json();
    if (!response.ok || !data.user) { $('loginError').textContent = data.error || 'Incorrect username or password. Please try again.'; return; }
    currentUser = data.user;
    sessionStorage.setItem(STORE.session, JSON.stringify({ username: currentUser.username, role: currentUser.role }));
    $('loginError').textContent = '';
    openApp();
  } catch (error) {
    $('loginError').textContent = 'Unable to contact the Java server. Please try again.';
  }
}
function logout() {
  currentUser = null; sessionStorage.removeItem(STORE.session);
  $('appView').classList.add('hidden'); $('loginView').classList.remove('hidden');
  $('loginPassword').value = ''; $('loginUsername').value = ''; $('loginError').textContent = '';
}
function openApp() {
  $('loginView').classList.add('hidden'); $('appView').classList.remove('hidden');
  $('topUserName').textContent = currentUser.name;
  $('topUserRole').textContent = isAdmin() ? 'Administrator' : 'Patient';
  $('userAvatar').textContent = initials(currentUser.name);
  buildNav(); renderPage(activePage);
}
function buildNav() {
  const navItems = isAdmin()
    ? [['overview','▦','Overview'],['queue','≋','Queue management'],['appointments','▣','Appointments'],['patients','♙','Patient accounts'],['doctors','✚','Doctors & schedules']]
    : [['overview','▦','My overview'],['book','＋','Book appointment'],['appointments','▣','My appointments'],['doctors','✚','Doctors & schedules']];
  $('sideNav').innerHTML = navItems.map(([key,icon,label]) => `<button class="nav-btn ${activePage===key?'active':''}" data-page="${key}"><span class="nav-icon">${icon}</span>${label}</button>`).join('');
  $('sideNav').querySelectorAll('button').forEach(button => button.addEventListener('click', () => renderPage(button.dataset.page)));
}
function renderPage(page) {
  activePage = page;
  const allowed = isAdmin() ? ['overview','queue','appointments','patients','doctors'] : ['overview','book','appointments','doctors'];
  if (!allowed.includes(page)) page = 'overview';
  activePage = page; buildNav();
  const titles = {overview:'Overview',queue:'Queue management',appointments:isAdmin()?'Appointments':'My appointments',patients:'Patient accounts',doctors:'Doctors & schedules',book:'Book an appointment'};
  $('pageTitle').textContent = titles[page] || 'Overview';
  $('pageEyebrow').textContent = isAdmin() ? 'ADMIN WORKSPACE' : 'PATIENT WORKSPACE';
  const renderers = {overview:renderOverview,queue:renderQueue,appointments:renderAppointments,patients:renderPatients,doctors:renderDoctors,book:renderBooking};
  $('pageContent').innerHTML = renderers[page]();
  attachPageEvents(page);
}
function visibleAppointments() {
  const all = getAppointments();
  return isAdmin() ? all : all.filter(a => a.username === currentUser.username);
}
function renderOverview() {
  const all = getAppointments();
  const data = isAdmin() ? all : all.filter(a => a.username === currentUser.username);
  const todayAppts = data.filter(a => a.date === today() && !['Cancelled','Completed'].includes(a.status));
  const waiting = data.filter(a => a.status === 'Waiting');
  const active = data.filter(a => a.status === 'In Consultation');
  const cards = isAdmin() ? [
    ['Total appointments',all.length,'▣','All recorded bookings'],['Waiting in queue',all.filter(a=>a.status==='Waiting').length,'≋','Patients awaiting consultation'],['In consultation',all.filter(a=>a.status==='In Consultation').length,'✚','Currently being served'],['Patient accounts',getUsers().filter(u=>u.role==='patient').length,'♙','Registered demo accounts']
  ] : [
    ['My appointments',data.length,'▣','All your bookings'],['Upcoming today',todayAppts.length,'◷','Active bookings for today'],['Waiting',waiting.length,'≋','Appointments waiting'],['In consultation',active.length,'✚','Currently being served']
  ];
  const recent = [...data].sort((a,b)=>(a.date+a.time).localeCompare(b.date+b.time)).slice(0,5);
  const appointmentRows = recent.length ? recent.map(a => `<tr><td><div class="patient-cell"><div class="mini-avatar">${initials(a.patient)}</div><div><strong>${esc(a.patient)}</strong><small>${esc(a.id)}</small></div></div></td><td>${esc(a.doctor)}</td><td>${prettyDate(a.date)}</td><td>${esc(a.time)}</td><td>${badge(a.status)}</td></tr>`).join('') : `<tr><td colspan="5"><div class="empty-state"><div class="empty-icon">▣</div><strong>No appointments yet</strong><p>${isAdmin()?'New bookings will appear here.':'Book your first appointment to get started.'}</p></div></td></tr>`;
  return `<div class="welcome-row"><div><h2>${isAdmin()?'Welcome, '+esc(currentUser.name.split(' ')[0]):'Hello, '+esc(currentUser.name)} 👋</h2><p>${isAdmin()?'Here is the current clinic activity.':'Here is a summary of your clinic appointments.'}</p></div>${!isAdmin()?'<button class="btn primary" data-action="go-book">＋ Book appointment</button>':''}</div>
    <div class="stats-grid">${cards.map(c=>`<div class="stat-card"><div class="stat-top"><span>${c[0]}</span><span class="stat-icon">${c[2]}</span></div><div class="stat-value">${c[1]}</div><div class="stat-note">${c[3]}</div></div>`).join('')}</div>
    <div class="content-grid"><section class="panel"><div class="panel-head"><div><h3>${isAdmin()?'Recent appointments':'My recent appointments'}</h3><p>Latest booking activity</p></div><button class="link-btn" data-action="go-appointments">View all →</button></div><div class="table-wrap"><table><thead><tr><th>Patient</th><th>Doctor</th><th>Date</th><th>Time</th><th>Status</th></tr></thead><tbody>${appointmentRows}</tbody></table></div></section>
    <section class="panel"><div class="panel-head"><div><h3>Available doctors</h3><p>Clinic directory</p></div><button class="link-btn" data-action="go-doctors">View all →</button></div><div class="doctor-list">${doctors.slice(0,4).map(d=>`<div class="doctor-item"><div class="doctor-avatar">${initials(d.name.replace('Dr. ','Dr '))}</div><div><strong>${esc(d.name)}</strong><small>${esc(d.specialty)}</small></div><span class="doctor-status" title="Available"></span></div>`).join('')}</div></section></div>`;
}
function renderBooking() {
  return `<div class="welcome-row"><div><h2>Schedule a clinic visit</h2><p>Choose a doctor and a convenient time for your appointment.</p></div></div><div class="section-note">Please arrive 10–15 minutes before your appointment. Appointment records are saved in the clinic SQLite database.</div><section class="panel"><div class="panel-head"><div><h3>Appointment details</h3><p>All fields marked by the form are required.</p></div></div><form id="bookingForm"><div class="field-grid"><div class="field"><label for="patientName">Patient name</label><input id="patientName" value="${esc(currentUser.name)}" required maxlength="80"></div><div class="field"><label for="patientContact">Contact number (optional)</label><input id="patientContact" maxlength="30" placeholder="09XX XXX XXXX"></div><div class="field"><label for="doctorSelect">Choose doctor</label><select id="doctorSelect" required>${doctors.map(d=>`<option value="${esc(d.name)}">${esc(d.name)} — ${esc(d.specialty)}</option>`).join('')}</select></div><div class="field"><label for="appointmentDate">Appointment date</label><input id="appointmentDate" type="date" min="${today()}" value="${today()}" required></div><div class="field"><label for="appointmentTime">Available time</label><select id="appointmentTime" required>${timeSlots().map(t=>`<option>${t}</option>`).join('')}</select></div><div class="field"><label for="visitType">Visit type</label><select id="visitType"><option>General consultation</option><option>Follow-up visit</option><option>Check-up</option><option>Other concern</option></select></div><div class="field full-field"><label for="appointmentReason">Reason for visit (optional)</label><textarea id="appointmentReason" maxlength="250" placeholder="Briefly describe your concern"></textarea></div></div><div class="form-actions"><button type="reset" class="btn light">Clear form</button><button class="btn primary" type="submit">Confirm appointment →</button></div></form></section>`;
}
function timeSlots() { return ['09:00 AM','09:30 AM','10:00 AM','10:30 AM','11:00 AM','11:30 AM','01:00 PM','01:30 PM','02:00 PM','02:30 PM','03:00 PM','03:30 PM','04:00 PM']; }
function renderAppointments() {
  const data = [...visibleAppointments()].sort((a,b)=>(a.date+a.time).localeCompare(b.date+b.time));
  const rows = data.length ? data.map(a=>`<tr><td><div class="patient-cell"><div class="mini-avatar">${initials(a.patient)}</div><div><strong>${esc(a.patient)}</strong><small>${esc(a.id)}${isAdmin()?' · @'+esc(a.username):''}</small></div></div></td><td>${esc(a.doctor)}</td><td>${prettyDate(a.date)}</td><td>${esc(a.time)}</td><td>${badge(a.status)}</td><td>${isAdmin()?`<select class="inline-select" data-status-id="${esc(a.id)}">${['Scheduled','Waiting','In Consultation','Completed','Cancelled'].map(s=>`<option ${a.status===s?'selected':''}>${s}</option>`).join('')}</select>`:a.status==='Scheduled'||a.status==='Waiting'?`<button class="btn danger small" data-cancel-id="${esc(a.id)}">Cancel</button>`:'—'}</td></tr>`).join('') : `<tr><td colspan="6"><div class="empty-state"><div class="empty-icon">▣</div><strong>No appointments found</strong><p>${isAdmin()?'Appointments booked by patients will show here.':'You do not have any appointments yet.'}</p>${!isAdmin()?'<button class="btn primary" data-action="go-book">Book appointment</button>':''}</div></td></tr>`;
  return `<div class="welcome-row"><div><h2>${isAdmin()?'Clinic appointments':'Your appointments'}</h2><p>${isAdmin()?'Review bookings and update their status.':'Check your appointment details or cancel a pending booking.'}</p></div>${!isAdmin()?'<button class="btn primary" data-action="go-book">＋ Book appointment</button>':''}</div><section class="panel"><div class="panel-head"><div><h3>Appointment records</h3><p>${data.length} record(s)</p></div></div><div class="table-wrap"><table><thead><tr><th>Patient</th><th>Doctor</th><th>Date</th><th>Time</th><th>Status</th><th>Action</th></tr></thead><tbody>${rows}</tbody></table></div></section>`;
}
function renderQueue() {
  const data = [...getAppointments()].filter(a=>['Waiting','In Consultation'].includes(a.status)).sort((a,b)=>(a.date+a.time).localeCompare(b.date+b.time));
  return `<div class="welcome-row"><div><h2>Live clinic queue</h2><p>Update the status as each patient moves through the clinic.</p></div><button class="btn light" data-action="refresh">↻ Refresh</button></div><div class="stats-grid"><div class="stat-card"><div class="stat-top">Waiting<span class="stat-icon">≋</span></div><div class="stat-value">${data.filter(a=>a.status==='Waiting').length}</div><div class="stat-note">Awaiting consultation</div></div><div class="stat-card"><div class="stat-top">In consultation<span class="stat-icon">✚</span></div><div class="stat-value">${data.filter(a=>a.status==='In Consultation').length}</div><div class="stat-note">Currently being served</div></div><div class="stat-card"><div class="stat-top">Total active<span class="stat-icon">▣</span></div><div class="stat-value">${data.length}</div><div class="stat-note">Active queue records</div></div><div class="stat-card"><div class="stat-top">Doctors<span class="stat-icon">♙</span></div><div class="stat-value">${doctors.length}</div><div class="stat-note">Listed in directory</div></div></div><section class="panel"><div class="panel-head"><div><h3>Queue order</h3><p>Appointments are listed by date and time.</p></div></div><div class="queue-list">${data.length?data.map((a,i)=>`<div class="queue-row"><div class="queue-number">${String(i+1).padStart(2,'0')}</div><div class="queue-info"><strong>${esc(a.patient)} ${badge(a.status)}</strong><small>${esc(a.id)} · ${esc(a.doctor)} · ${prettyDate(a.date)} at ${esc(a.time)}</small></div><div class="queue-actions">${a.status==='Waiting'?`<button class="btn primary small" data-set-status="${esc(a.id)}" data-next-status="In Consultation">Start consultation</button>`:''}<button class="btn success small" data-set-status="${esc(a.id)}" data-next-status="Completed">Complete</button><button class="btn light small" data-set-status="${esc(a.id)}" data-next-status="Waiting">Mark waiting</button></div></div>`).join(''):'<div class="empty-state"><div class="empty-icon">≋</div><strong>The queue is clear</strong><p>New active appointments will appear here.</p></div>'}</div></section>`;
}
function renderPatients() {
  const users = getUsers().filter(u=>u.role==='patient');
  const appts = getAppointments();
  return `<div class="welcome-row"><div><h2>Patient accounts</h2><p>Registered patient accounts stored in the clinic database.</p></div></div><div class="section-note">For privacy, this page does not display patient passwords. Account passwords are checked by the Java backend and are not sent to the browser.</div><section class="panel"><div class="panel-head"><div><h3>Registered patients</h3><p>${users.length} patient account(s)</p></div></div><div class="table-wrap"><table><thead><tr><th>Patient</th><th>Username</th><th>Appointments</th><th>Account type</th></tr></thead><tbody>${users.map(u=>`<tr><td><div class="patient-cell"><div class="mini-avatar">${initials(u.name)}</div><div><strong>${esc(u.name)}</strong><small>Patient account</small></div></div></td><td>${esc(u.username)}</td><td>${appts.filter(a=>a.username===u.username).length}</td><td><span class="badge completed">Patient</span></td></tr>`).join('')}</tbody></table></div></section>`;
}
function renderDoctors() {
  return `<div class="welcome-row"><div><h2>Doctors & schedules</h2><p>Browse the clinic's listed doctors and regular consultation hours.</p></div></div><div class="stats-grid">${doctors.map(d=>`<div class="stat-card"><div class="stat-top"><span>Clinic doctor</span><span class="stat-icon">✚</span></div><div class="stat-value" style="font-size:18px">${esc(d.name)}</div><div style="font-weight:700;color:#52627a;font-size:12px;margin-bottom:8px">${esc(d.specialty)}</div><div class="stat-note">Regular hours: ${esc(d.hours)}</div></div>`).join('')}</div><section class="panel"><div class="panel-head"><div><h3>Appointment time slots</h3><p>Available times shown in the booking form.</p></div></div><div class="queue-list">${timeSlots().map(t=>`<div class="queue-row"><div class="queue-number">◷</div><div class="queue-info"><strong>${t}</strong><small>30-minute booking interval</small></div><span class="badge completed">Listed</span></div>`).join('')}</div></section>`;
}
function attachPageEvents(page) {
  document.querySelectorAll('[data-action]').forEach(el=>el.addEventListener('click',()=>{
    const action=el.dataset.action;
    if(action==='go-book') renderPage('book'); else if(action==='go-appointments') renderPage('appointments'); else if(action==='go-doctors') renderPage('doctors'); else if(action==='refresh') renderPage('queue');
  }));
  if(page==='book') $('bookingForm').addEventListener('submit',createAppointment);
  document.querySelectorAll('[data-cancel-id]').forEach(el=>el.addEventListener('click',()=>updateStatus(el.dataset.cancelId,'Cancelled')));
  document.querySelectorAll('[data-status-id]').forEach(el=>el.addEventListener('change',()=>updateStatus(el.dataset.statusId,el.value)));
  document.querySelectorAll('[data-set-status]').forEach(el=>el.addEventListener('click',()=>updateStatus(el.dataset.setStatus,el.dataset.nextStatus)));
}
function createAppointment(event) {
  event.preventDefault();
  const patient=$('patientName').value.trim(), date=$('appointmentDate').value, time=$('appointmentTime').value, doctor=$('doctorSelect').value;
  if(!patient || !date || date<today()) { showToast('Please enter a valid patient name and date.'); return; }
  const appointments=getAppointments();
  const duplicate=appointments.some(a=>a.doctor===doctor&&a.date===date&&a.time===time&&!['Cancelled','Completed'].includes(a.status));
  if(duplicate) { showToast('That doctor and time are already booked. Please choose another slot.'); return; }
  const nextId=Math.max(1000,...appointments.map(a=>Number(String(a.id).replace(/\D/g,''))||1000))+1;
  appointments.push({id:'MQ-'+nextId,username:currentUser.username,patient,contact:$('patientContact').value.trim(),doctor,date,time,visitType:$('visitType').value,reason:$('appointmentReason').value.trim(),status:'Scheduled',createdAt:new Date().toISOString()});
  saveAppointments(appointments); showToast('Appointment booked successfully.'); renderPage('appointments');
}
function updateStatus(id,status) {
  const appointments=getAppointments();
  const item=appointments.find(a=>a.id===id);
  if(!item) return;
  if(!isAdmin() && item.username!==currentUser.username) { showToast('You can only update your own appointment.'); return; }
  if(!isAdmin() && status!=='Cancelled') { showToast('Only the clinic administrator can update queue status.'); return; }
  item.status=status; saveAppointments(appointments); showToast('Appointment status updated.'); renderPage(activePage);
}
document.addEventListener('DOMContentLoaded',init);
