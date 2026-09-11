import { BrowserRouter, Routes, Route } from 'react-router-dom';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import Dashboard from './pages/Dashboard';
import AdminDashboard from './pages/AdminDashboard';
import TransferPage from './pages/TransferPage';
import DepositPage from './pages/DepositPage';
import WithdrawPage from './pages/WithdrawPage';
import CreateAccountPage from './pages/CreateAccountPage';
import TransferOwnershipPage from './pages/TransferOwnershipPage';
import DeleteAccountPage from './pages/DeleteAccountPage';
import { NotificationProvider } from './components/NotificationProvider';

function App() {
  return (
    <BrowserRouter>
      <NotificationProvider>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/admin" element={<AdminDashboard />} />
        <Route path="/transfer" element={<TransferPage />} />
        <Route path="/deposit" element={<DepositPage />} />
        <Route path="/withdraw" element={<WithdrawPage />} />
        <Route path="/create-account" element={<CreateAccountPage />} />
        <Route path="/transfer-ownership" element={<TransferOwnershipPage />} />
        <Route path="/delete-account" element={<DeleteAccountPage />} />
      </Routes>
      </NotificationProvider>
    </BrowserRouter>
  );
}

export default App;
