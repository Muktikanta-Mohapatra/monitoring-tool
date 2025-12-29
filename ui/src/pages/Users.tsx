import styled from 'styled-components';
import React, { useState, useEffect, useCallback } from 'react';
import { Plus, Edit2, Trash2, Shield, User, RefreshCw, AlertCircle } from 'lucide-react';
import { PageHeader, PageTitle, PageSubtitle, PageActions } from '../components/Common';
import { Button, Tabs, Table, TableContainer, TableHead, TableBody, TableRow, TableHeader, TableCell, Badge, Modal, Input, Select, Spinner } from '../components/UI';
import { api } from '../utils/api';
import type { UserDTO } from '../types';

const ContentArea = styled.div`
  margin-top: ${({ theme }) => theme.spacing.lg};
`;

const UserCard = styled.div`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-radius: ${({ theme }) => theme.radius.md};
  overflow: hidden;
`;

const UserAvatar = styled.div<{ $color: string }>`
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: ${({ $color }) => $color};
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 600;
  font-size: 14px;
`;

const UserInfo = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.md};
`;

const UserDetails = styled.div``;

const UserName = styled.div`
  font-size: 14px;
  font-weight: 500;
  color: ${({ theme }) => theme.colors.text.primary};
`;

const UserEmail = styled.div`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.text.secondary};
`;

const ActionsCell = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.xs};
`;

const IconButton = styled.button`
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: ${({ theme }) => theme.radius.sm};
  color: ${({ theme }) => theme.colors.text.secondary};
  transition: all ${({ theme }) => theme.transition.fast};

  &:hover {
    background: ${({ theme }) => theme.colors.bg.tertiary};
    color: ${({ theme }) => theme.colors.text.primary};
  }
`;

const ModalContent = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.lg};
`;

const ModalActions = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: ${({ theme }) => theme.spacing.sm};
  margin-top: ${({ theme }) => theme.spacing.lg};
  padding-top: ${({ theme }) => theme.spacing.lg};
  border-top: 1px solid ${({ theme }) => theme.colors.border.default};
`;

const tabs = [
  { id: 'users', label: 'Users', icon: <User size={16} /> },
  { id: 'roles', label: 'Roles', icon: <Shield size={16} /> },
];

interface UserData {
  id: string;
  name: string;
  email: string;
  role: string;
  status: 'active' | 'inactive';
  lastLogin: string;
  color: string;
}

interface RoleData {
  id: string;
  name: string;
  description: string;
  users: number;
  permissions: string;
}

const AVATAR_COLORS = ['#32B8C6', '#E67F48', '#3DCC71', '#F0AD4E', '#5AB1D1', '#9B59B6', '#E74C3C'];

const getAvatarColor = (id: string): string => {
  const hash = id.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
};

const formatLastLogin = (lastLogin?: string): string => {
  if (!lastLogin) return 'Never';
  try {
    const date = new Date(lastLogin);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffSecs = Math.floor(diffMs / 1000);
    if (diffSecs < 60) return 'Just now';
    if (diffSecs < 3600) return `${Math.floor(diffSecs / 60)}m ago`;
    if (diffSecs < 86400) return `${Math.floor(diffSecs / 3600)}h ago`;
    if (diffSecs < 604800) return `${Math.floor(diffSecs / 86400)}d ago`;
    return date.toLocaleDateString();
  } catch {
    return 'Unknown';
  }
};

const mapUserDTOToUserData = (dto: UserDTO): UserData => ({
  id: String(dto.id),
  name: dto.username || 'Unknown',
  email: dto.email || '',
  role: dto.role || 'Viewer',
  status: dto.enabled !== false ? 'active' : 'inactive',
  lastLogin: formatLastLogin(dto.lastLogin),
  color: getAvatarColor(String(dto.id)),
});

const LoadingContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: ${({ theme }) => theme.spacing['2xl']};
  gap: ${({ theme }) => theme.spacing.lg};
  background: ${({ theme }) => theme.colors.bg.secondary};
  border-radius: ${({ theme }) => theme.radius.md};
  min-height: 200px;
`;

const ErrorContainer = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${({ theme }) => theme.spacing.md};
  padding: ${({ theme }) => theme.spacing.lg};
  background: rgba(255, 84, 89, 0.1);
  border: 1px solid rgba(255, 84, 89, 0.3);
  border-radius: ${({ theme }) => theme.radius.md};
  margin-bottom: ${({ theme }) => theme.spacing.lg};
  color: #ff5459;
`;

const POLLING_INTERVAL = 30000;

const getInitials = (name: string) => {
  return name.split(' ').map(n => n[0]).join('').toUpperCase();
};

export const Users: React.FC = () => {
  const [activeTab, setActiveTab] = useState('users');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [users, setUsers] = useState<UserData[]>([]);
  const [roles, setRoles] = useState<RoleData[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [newUser, setNewUser] = useState({ name: '', email: '', role: '' });

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.getUsers();
      const mappedUsers = response.map(mapUserDTOToUserData);
      setUsers(mappedUsers);

      const roleCounts = mappedUsers.reduce((acc, user) => {
        acc[user.role] = (acc[user.role] || 0) + 1;
        return acc;
      }, {} as Record<string, number>);

      setRoles([
        { id: '1', name: 'Admin', description: 'Full system access', users: roleCounts['Admin'] || 0, permissions: 'All' },
        { id: '2', name: 'Analyst', description: 'Search and analyze logs', users: roleCounts['Analyst'] || 0, permissions: 'Read, Search, Export' },
        { id: '3', name: 'Viewer', description: 'View-only access', users: roleCounts['Viewer'] || 0, permissions: 'Read' },
      ]);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load users';
      setError(message);
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  useEffect(() => {
    const interval = setInterval(loadUsers, POLLING_INTERVAL);
    return () => clearInterval(interval);
  }, [loadUsers]);

  const handleCreateUser = useCallback(async () => {
    if (!newUser.name || !newUser.email || !newUser.role) return;
    try {
      const roleMap: Record<string, 'ADMIN' | 'OPERATOR' | 'VIEWER'> = {
        admin: 'ADMIN',
        analyst: 'OPERATOR',
        viewer: 'VIEWER',
      };
      await api.createUser({
        username: newUser.name,
        email: newUser.email,
        role: roleMap[newUser.role] || 'VIEWER',
      });
      setIsModalOpen(false);
      setNewUser({ name: '', email: '', role: '' });
      loadUsers();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create user');
    }
  }, [newUser, loadUsers]);

  const handleDeleteUser = useCallback(async (id: string) => {
    try {
      await api.deleteUser(Number(id));
      loadUsers();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete user');
    }
  }, [loadUsers]);

  return (
    <>
      <PageHeader>
        <div>
          <PageTitle>Users & Permissions</PageTitle>
          <PageSubtitle>Manage user accounts and access control</PageSubtitle>
        </div>
        <PageActions>
          <Button 
            variant="secondary" 
            icon={<RefreshCw size={16} />} 
            onClick={loadUsers}
            loading={loading}
          >
            Refresh
          </Button>
          <Button icon={<Plus size={16} />} onClick={() => setIsModalOpen(true)}>
            Add User
          </Button>
        </PageActions>
      </PageHeader>

      {error && (
        <ErrorContainer>
          <AlertCircle size={20} />
          <div>{error}</div>
        </ErrorContainer>
      )}

      <Tabs tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />

      <ContentArea>
        {loading && users.length === 0 && (
          <LoadingContainer>
            <Spinner />
            <p>Loading users...</p>
          </LoadingContainer>
        )}
        {activeTab === 'users' && !loading && (
          <UserCard>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableHeader>User</TableHeader>
                    <TableHeader>Role</TableHeader>
                    <TableHeader>Status</TableHeader>
                    <TableHeader>Last Login</TableHeader>
                    <TableHeader>Actions</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {users.map((user) => (
                    <TableRow key={user.id}>
                      <TableCell>
                        <UserInfo>
                          <UserAvatar $color={user.color}>
                            {getInitials(user.name)}
                          </UserAvatar>
                          <UserDetails>
                            <UserName>{user.name}</UserName>
                            <UserEmail>{user.email}</UserEmail>
                          </UserDetails>
                        </UserInfo>
                      </TableCell>
                      <TableCell>
                        <Badge $variant={user.role === 'Admin' ? 'info' : 'default'}>
                          {user.role}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge $variant={user.status === 'active' ? 'success' : 'default'}>
                          {user.status.toUpperCase()}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <span style={{ color: '#A1A9A8', fontSize: '13px' }}>{user.lastLogin}</span>
                      </TableCell>
                      <TableCell>
                        <ActionsCell>
                          <IconButton title="Edit"><Edit2 size={14} /></IconButton>
                          <IconButton title="Delete" onClick={() => handleDeleteUser(user.id)}><Trash2 size={14} /></IconButton>
                        </ActionsCell>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </UserCard>
        )}

        {activeTab === 'roles' && !loading && (
          <UserCard>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableHeader>Role Name</TableHeader>
                    <TableHeader>Description</TableHeader>
                    <TableHeader>Users</TableHeader>
                    <TableHeader>Permissions</TableHeader>
                    <TableHeader>Actions</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {roles.map((role) => (
                    <TableRow key={role.id}>
                      <TableCell>
                        <span style={{ fontWeight: 500 }}>{role.name}</span>
                      </TableCell>
                      <TableCell>
                        <span style={{ color: '#A1A9A8' }}>{role.description}</span>
                      </TableCell>
                      <TableCell>{role.users}</TableCell>
                      <TableCell>
                        <span style={{ color: '#A1A9A8', fontSize: '13px' }}>{role.permissions}</span>
                      </TableCell>
                      <TableCell>
                        <ActionsCell>
                          <IconButton><Edit2 size={14} /></IconButton>
                        </ActionsCell>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </UserCard>
        )}
      </ContentArea>

      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title="Add New User" size="md">
        <ModalContent>
          <Input 
            label="Full Name" 
            placeholder="Enter full name" 
            required 
            value={newUser.name}
            onChange={(e) => setNewUser(prev => ({ ...prev, name: e.target.value }))}
          />
          <Input 
            label="Email Address" 
            type="email" 
            placeholder="Enter email address" 
            required 
            value={newUser.email}
            onChange={(e) => setNewUser(prev => ({ ...prev, email: e.target.value }))}
          />
          <Select
            label="Role"
            options={[
              { value: 'admin', label: 'Admin' },
              { value: 'analyst', label: 'Analyst' },
              { value: 'viewer', label: 'Viewer' },
            ]}
            value={newUser.role}
            onChange={(value) => setNewUser(prev => ({ ...prev, role: value }))}
            placeholder="Select a role"
          />
          <ModalActions>
            <Button variant="secondary" onClick={() => setIsModalOpen(false)}>Cancel</Button>
            <Button onClick={handleCreateUser}>Create User</Button>
          </ModalActions>
        </ModalContent>
      </Modal>
    </>
  );
};
