import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './lib/auth';
import Layout from './components/Layout';
import { RequireAuth } from './components/ui';
import Home from './pages/Home';
import Opportunities from './pages/Opportunities';
import OpportunityDetail from './pages/OpportunityDetail';
import CatalogList from './pages/CatalogList';
import CatalogDetail from './pages/CatalogDetail';
import { ForgotPassword, Login, Register, ResetPassword, VerifyEmail } from './pages/Auth';
import Me from './pages/Me';
import Membership from './pages/Membership';
import Contact from './pages/Contact';
import NotFound from './pages/NotFound';
import { CATALOG } from './lib/format';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<Home />} />
            <Route path="opportunities" element={<Opportunities />} />
            <Route path="opportunities/:slug" element={<OpportunityDetail />} />
            {Object.entries(CATALOG).map(([type, cfg]) => [
              <Route key={type} path={cfg.path.slice(1)} element={<CatalogList key={type} type={type} />} />,
              <Route key={type + 'd'} path={`${cfg.path.slice(1)}/:slug`} element={<CatalogDetail type={type} />} />,
            ])}
            <Route path="subscription" element={<Membership />} />
            <Route path="login" element={<Login />} />
            <Route path="register" element={<Register />} />
            <Route path="forgot-password" element={<ForgotPassword />} />
            <Route path="reset-password" element={<ResetPassword />} />
            <Route path="verify-email" element={<VerifyEmail />} />
            <Route path="membership" element={<Membership />} />
            <Route path="contact" element={<Contact />} />
            <Route path="me" element={<RequireAuth><Me /></RequireAuth>} />
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>
);
