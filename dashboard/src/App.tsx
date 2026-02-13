import { useState, useEffect } from 'react'
import { BrowserRouter as Router, Routes, Route, NavLink } from 'react-router-dom'
import { Home, Receipt, BarChart3, Settings } from 'lucide-react'
import Dashboard from './pages/Dashboard'
import Transactions from './pages/Transactions'
import Reports from './pages/Reports'
import SettingsPage from './pages/Settings'

function App() {
  const [isConnected, setIsConnected] = useState(false)

  useEffect(() => {
    // Check API connection
    fetch('/api/health')
      .then(res => res.json())
      .then(() => setIsConnected(true))
      .catch(() => setIsConnected(false))
  }, [])

  return (
    <Router basename="/dashboard">
      <div className="min-h-screen bg-gray-50">
        {/* Header */}
        <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="flex justify-between items-center h-16">
              <div className="flex items-center space-x-3">
                <div className="w-8 h-8 bg-primary-500 rounded-lg flex items-center justify-center">
                  <span className="text-white font-bold">S</span>
                </div>
                <h1 className="text-xl font-bold text-gray-900">RupeeLog</h1>
              </div>

              <div className="flex items-center space-x-2">
                <span className={`w-2 h-2 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`}></span>
                <span className="text-sm text-gray-500">
                  {isConnected ? 'Connected' : 'Disconnected'}
                </span>
              </div>
            </div>
          </div>
        </header>

        <div className="flex">
          {/* Sidebar Navigation */}
          <nav className="w-64 bg-white border-r border-gray-200 min-h-[calc(100vh-4rem)] hidden md:block">
            <div className="p-4 space-y-2">
              <NavItem to="/" icon={<Home size={20} />} label="Dashboard" />
              <NavItem to="/transactions" icon={<Receipt size={20} />} label="Transactions" />
              <NavItem to="/reports" icon={<BarChart3 size={20} />} label="Reports" />
              <NavItem to="/settings" icon={<Settings size={20} />} label="Settings" />
            </div>
          </nav>

          {/* Main Content */}
          <main className="flex-1 p-6">
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/transactions" element={<Transactions />} />
              <Route path="/reports" element={<Reports />} />
              <Route path="/settings" element={<SettingsPage />} />
            </Routes>
          </main>
        </div>

        {/* Mobile Bottom Navigation */}
        <nav className="md:hidden fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200">
          <div className="flex justify-around py-3">
            <MobileNavItem to="/" icon={<Home size={24} />} label="Home" />
            <MobileNavItem to="/transactions" icon={<Receipt size={24} />} label="Transactions" />
            <MobileNavItem to="/reports" icon={<BarChart3 size={24} />} label="Reports" />
            <MobileNavItem to="/settings" icon={<Settings size={24} />} label="Settings" />
          </div>
        </nav>
      </div>
    </Router>
  )
}

function NavItem({ to, icon, label }: { to: string; icon: React.ReactNode; label: string }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `flex items-center space-x-3 px-4 py-3 rounded-lg transition-colors ${
          isActive
            ? 'bg-primary-50 text-primary-600'
            : 'text-gray-600 hover:bg-gray-50'
        }`
      }
    >
      {icon}
      <span className="font-medium">{label}</span>
    </NavLink>
  )
}

function MobileNavItem({ to, icon, label }: { to: string; icon: React.ReactNode; label: string }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `flex flex-col items-center space-y-1 ${
          isActive ? 'text-primary-600' : 'text-gray-500'
        }`
      }
    >
      {icon}
      <span className="text-xs">{label}</span>
    </NavLink>
  )
}

export default App
